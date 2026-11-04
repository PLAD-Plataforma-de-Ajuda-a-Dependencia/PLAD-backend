package plad.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.AnswerRequestDTO;
import plad.dto.AnswerResponseDTO;
import plad.dto.OptionDTO;
import plad.dto.QuestionResponseDTO;
import plad.dto.TriageRequestDTO;
import plad.dto.TriageResponseDTO;
import plad.entity.AuditAction;
import plad.entity.Dependent;
import plad.entity.QuestionTriage;
import plad.entity.ResultQuestionTriage;
import plad.entity.Triage;
import plad.entity.UserType;
import plad.exception.ForbiddenActionException;
import plad.exception.InvalidTriageException;
import plad.exception.TriageAlreadyExistException;
import plad.exception.TriageNotFoundException;
import plad.repositories.DependentRepository;
import plad.repositories.ResultQuestionTriageRepository;
import plad.repositories.TriageRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TriageService {

    private static final int MAX_FREE_TEXT_LENGTH = 1000;
    private static final String AUDIT_ENTITY = "TRIAGE";

    private final TriageQuestionCatalog questionCatalog;
    private final TriageRepository triageRepository;
    private final ResultQuestionTriageRepository resultQuestionTriageRepository;
    private final DependentRepository dependentRepository;
    private final AuditService auditService;

    //constructor injection
    public TriageService(TriageQuestionCatalog questionCatalog, TriageRepository triageRepository, ResultQuestionTriageRepository resultQuestionTriageRepository, DependentRepository dependentRepository, AuditService auditService) {
        this.questionCatalog = questionCatalog;
        this.triageRepository = triageRepository;
        this.resultQuestionTriageRepository = resultQuestionTriageRepository;
        this.dependentRepository = dependentRepository;
        this.auditService = auditService;
    }

    // a question that passed validation, with the values to persist
    private record ValidatedAnswer(QuestionTriage question, List<String> values) {
    }

    @Transactional(readOnly = true)
    public List<QuestionResponseDTO> getQuestions() {
        return questionCatalog.findAllOrdered().stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    // empty when nothing was answered: no triage is stored, so the dependent can still do it later
    @Transactional
    public Optional<TriageResponseDTO> submit(Long userId, TriageRequestDTO request) {
        Dependent dependent = findDependent(userId);

        if (triageRepository.existsByDependentUserId(userId)) {
            throw new TriageAlreadyExistException("Triage already submitted");
        }

        List<ValidatedAnswer> answers = validate(request);
        if (answers.isEmpty()) {
            return Optional.empty();
        }

        Triage triage = new Triage();
        triage.setDependent(dependent);
        try {
            // flush now so a concurrent duplicate hits the unique constraint here and not at commit
            triage = triageRepository.saveAndFlush(triage);
        } catch (DataIntegrityViolationException e) {
            throw new TriageAlreadyExistException("Triage already submitted");
        }

        saveResults(triage, answers);
        auditTriageWrite(AuditAction.TRIAGE_SUBMITTED, userId, triage);
        return Optional.of(toTriageResponse(triage, answers));
    }

    @Transactional
    public TriageResponseDTO resubmit(Long userId, TriageRequestDTO request) {
        findDependent(userId);

        Triage triage = triageRepository.findForUpdateByDependentUserId(userId)
                .orElseThrow(() -> new TriageNotFoundException("Triage not found"));

        List<ValidatedAnswer> answers = validate(request);
        if (answers.isEmpty()) {
            throw new InvalidTriageException("At least one question must be answered");
        }

        resultQuestionTriageRepository.deleteByTriageId(triage.getId());
        saveResults(triage, answers);
        auditTriageWrite(AuditAction.TRIAGE_RESUBMITTED, userId, triage);
        return toTriageResponse(triage, answers);
    }

    // ids and role only: the answers never reach the audit log
    private void auditTriageWrite(AuditAction action, Long userId, Triage triage) {
        auditService.record(action, userId, UserType.dependente, userId, AUDIT_ENTITY, triage.getId(), null);
    }

    private Dependent findDependent(Long userId) {
        return dependentRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenActionException("Only dependents can answer the triage"));
    }

    private List<ValidatedAnswer> validate(TriageRequestDTO request) {
        Map<Long, QuestionTriage> questions = questionCatalog.findAllOrdered().stream()
                .collect(Collectors.toMap(QuestionTriage::getId, Function.identity()));
        Map<String, QuestionTriage> questionsByCode = questions.values().stream()
                .collect(Collectors.toMap(QuestionTriage::getCode, Function.identity()));

        Set<Long> answered = new HashSet<>();
        List<ValidatedAnswer> validated = new ArrayList<>();

        for (AnswerRequestDTO answer : request.answers()) {
            QuestionTriage question = resolveQuestion(answer, questions, questionsByCode);
            if (!answered.add(question.getId())) {
                throw new InvalidTriageException("Duplicate answer for question " + question.getId());
            }
            validated.add(new ValidatedAnswer(question, validateValues(question, answer.values())));
        }

        validated.sort(Comparator.comparingInt(a -> a.question().getDisplayOrder()));
        return validated;
    }

    // an answer names its question by id, by code, or by both (which must then agree)
    private QuestionTriage resolveQuestion(AnswerRequestDTO answer, Map<Long, QuestionTriage> byId, Map<String, QuestionTriage> byCode) {
        if (answer.questionId() == null && (answer.questionCode() == null || answer.questionCode().isBlank())) {
            throw new InvalidTriageException("Each answer needs question_id or question_code");
        }

        QuestionTriage byIdMatch = null;
        if (answer.questionId() != null) {
            byIdMatch = byId.get(answer.questionId());
            if (byIdMatch == null) {
                throw new InvalidTriageException("Unknown question: " + answer.questionId());
            }
        }

        QuestionTriage byCodeMatch = null;
        if (answer.questionCode() != null && !answer.questionCode().isBlank()) {
            byCodeMatch = byCode.get(answer.questionCode());
            if (byCodeMatch == null) {
                throw new InvalidTriageException("Unknown question: " + answer.questionCode());
            }
        }

        if (byIdMatch != null && byCodeMatch != null && !byIdMatch.getId().equals(byCodeMatch.getId())) {
            throw new InvalidTriageException("question_id " + answer.questionId() + " does not match question_code " + answer.questionCode());
        }
        return byIdMatch != null ? byIdMatch : byCodeMatch;
    }

    private List<String> validateValues(QuestionTriage question, List<String> values) {
        return switch (question.getQuestionType()) {
            case single_choice -> {
                if (values.size() != 1) {
                    throw new InvalidTriageException("Question " + question.getId() + " accepts exactly one option");
                }
                requireKnownCodes(question, values);
                yield values;
            }
            case multi_choice -> {
                if (new HashSet<>(values).size() != values.size()) {
                    throw new InvalidTriageException("Question " + question.getId() + " has repeated options");
                }
                requireKnownCodes(question, values);
                yield values;
            }
            case free_text -> {
                if (values.size() != 1) {
                    throw new InvalidTriageException("Question " + question.getId() + " accepts exactly one text");
                }
                String text = values.get(0).strip();
                if (text.isEmpty()) {
                    throw new InvalidTriageException("Question " + question.getId() + " text must not be blank");
                }
                if (text.length() > MAX_FREE_TEXT_LENGTH) {
                    throw new InvalidTriageException("Question " + question.getId() + " text exceeds " + MAX_FREE_TEXT_LENGTH + " characters");
                }
                yield List.of(text);
            }
        };
    }

    private void requireKnownCodes(QuestionTriage question, List<String> values) {
        Set<String> validCodes = question.getOptions() == null
                ? Set.of()
                : question.getOptions().stream().map(option -> option.get("code")).collect(Collectors.toSet());
        for (String value : values) {
            if (!validCodes.contains(value)) {
                throw new InvalidTriageException("Invalid option '" + value + "' for question " + question.getId());
            }
        }
    }

    private void saveResults(Triage triage, List<ValidatedAnswer> answers) {
        List<ResultQuestionTriage> rows = new ArrayList<>();
        for (ValidatedAnswer answer : answers) {
            for (String value : answer.values()) {
                ResultQuestionTriage row = new ResultQuestionTriage();
                row.setTriage(triage);
                row.setQuestion(answer.question());
                row.setValueResult(value);
                rows.add(row);
            }
        }
        resultQuestionTriageRepository.saveAll(rows);
    }

    private QuestionResponseDTO toQuestionResponse(QuestionTriage question) {
        List<OptionDTO> options = question.getOptions() == null
                ? List.of()
                : question.getOptions().stream()
                        .map(option -> new OptionDTO(option.get("code"), option.get("label")))
                        .toList();
        return new QuestionResponseDTO(question.getId(), question.getCode(), question.getQuestionText(), question.getQuestionType(), options, question.getDisplayOrder(), question.isRequired());
    }

    private TriageResponseDTO toTriageResponse(Triage triage, List<ValidatedAnswer> answers) {
        List<AnswerResponseDTO> response = answers.stream()
                .map(a -> new AnswerResponseDTO(a.question().getId(), a.question().getCode(), a.question().getQuestionText(), a.values()))
                .toList();
        return new TriageResponseDTO(triage.getId(), triage.getCreatedAt(), response);
    }
}
