package pladBack.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pladBack.DTO.AnswerRequestDTO;
import pladBack.DTO.AnswerResponseDTO;
import pladBack.DTO.OptionDTO;
import pladBack.DTO.QuestionResponseDTO;
import pladBack.DTO.TriageRequestDTO;
import pladBack.DTO.TriageResponseDTO;
import pladBack.entity.Dependent;
import pladBack.entity.QuestionTriage;
import pladBack.entity.ResultQuestionTriage;
import pladBack.entity.Triage;
import pladBack.exception.ForbiddenActionException;
import pladBack.exception.InvalidTriageException;
import pladBack.exception.TriageAlreadyExistException;
import pladBack.exception.TriageNotFoundException;
import pladBack.repositories.dependentRepository;
import pladBack.repositories.questionTriageRepository;
import pladBack.repositories.resultQuestionTriageRepository;
import pladBack.repositories.triageRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TriageService {

    private static final int MAX_FREE_TEXT_LENGTH = 1000;

    private final questionTriageRepository questionTriageRepository;
    private final triageRepository triageRepository;
    private final resultQuestionTriageRepository resultQuestionTriageRepository;
    private final dependentRepository dependentRepository;

    //constructor injection
    public TriageService(questionTriageRepository questionTriageRepository, triageRepository triageRepository, resultQuestionTriageRepository resultQuestionTriageRepository, dependentRepository dependentRepository) {
        this.questionTriageRepository = questionTriageRepository;
        this.triageRepository = triageRepository;
        this.resultQuestionTriageRepository = resultQuestionTriageRepository;
        this.dependentRepository = dependentRepository;
    }

    // a question that passed validation, with the values to persist
    private record ValidatedAnswer(QuestionTriage question, List<String> values) {
    }

    @Transactional(readOnly = true)
    public List<QuestionResponseDTO> getQuestions() {
        return questionTriageRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toQuestionResponse)
                .toList();
    }

    @Transactional
    public TriageResponseDTO submit(Long userId, TriageRequestDTO request) {
        Dependent dependent = findDependent(userId);

        if (triageRepository.existsByDependentUserId(userId)) {
            throw new TriageAlreadyExistException("Triage already submitted");
        }

        List<ValidatedAnswer> answers = validate(request);

        Triage triage = new Triage();
        triage.setDependent(dependent);
        try {
            // flush now so a concurrent duplicate hits the unique constraint here and not at commit
            triage = triageRepository.saveAndFlush(triage);
        } catch (DataIntegrityViolationException e) {
            throw new TriageAlreadyExistException("Triage already submitted");
        }

        saveResults(triage, answers);
        return toTriageResponse(triage, answers);
    }

    @Transactional
    public TriageResponseDTO resubmit(Long userId, TriageRequestDTO request) {
        findDependent(userId);

        Triage triage = triageRepository.findForUpdateByDependentUserId(userId)
                .orElseThrow(() -> new TriageNotFoundException("Triage not found"));

        List<ValidatedAnswer> answers = validate(request);

        resultQuestionTriageRepository.deleteByTriageId(triage.getId());
        saveResults(triage, answers);
        return toTriageResponse(triage, answers);
    }

    private Dependent findDependent(Long userId) {
        return dependentRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenActionException("Only dependents can answer the triage"));
    }

    private List<ValidatedAnswer> validate(TriageRequestDTO request) {
        Map<Long, QuestionTriage> questions = questionTriageRepository.findAll().stream()
                .collect(Collectors.toMap(QuestionTriage::getId, Function.identity()));

        Set<Long> answered = new HashSet<>();
        List<ValidatedAnswer> validated = new ArrayList<>();

        for (AnswerRequestDTO answer : request.answers()) {
            QuestionTriage question = questions.get(answer.questionId());
            if (question == null) {
                throw new InvalidTriageException("Unknown question: " + answer.questionId());
            }
            if (!answered.add(question.getId())) {
                throw new InvalidTriageException("Duplicate answer for question " + question.getId());
            }
            validated.add(new ValidatedAnswer(question, validateValues(question, answer.values())));
        }

        validated.sort(Comparator.comparingInt(a -> a.question().getDisplayOrder()));
        return validated;
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
        return new QuestionResponseDTO(question.getId(), question.getQuestionText(), question.getQuestionType(), options, question.getDisplayOrder());
    }

    private TriageResponseDTO toTriageResponse(Triage triage, List<ValidatedAnswer> answers) {
        List<AnswerResponseDTO> response = answers.stream()
                .map(a -> new AnswerResponseDTO(a.question().getId(), a.question().getQuestionText(), a.values()))
                .toList();
        return new TriageResponseDTO(triage.getId(), triage.getCreatedAt(), response);
    }
}
