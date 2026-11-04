package plad.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.AdminTriageReadResponseDTO;
import plad.dto.AnswerResponseDTO;
import plad.dto.DependentIdentityDTO;
import plad.dto.DependentNicknameDTO;
import plad.dto.PageResponseDTO;
import plad.dto.TriageReadResponseDTO;
import plad.entity.AuditAction;
import plad.entity.Dependent;
import plad.entity.QuestionTriage;
import plad.entity.ResultQuestionTriage;
import plad.entity.Triage;
import plad.entity.UserType;
import plad.exception.InvalidCredentialsException;
import plad.exception.TriageNotFoundException;
import plad.repositories.ResultQuestionTriageRepository;
import plad.repositories.TriageRepository;
import plad.repositories.UserRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Reading triages for admins and psychologists. Every triage returned writes one TRIAGE_VIEWED entry in the
// same transaction, so reading the same triage twice leaves two entries. The route's @PreAuthorize decides who
// gets here; the role recorded in the entry comes from the database.
@Service
public class TriageReadService {

    private static final String AUDIT_ENTITY = "TRIAGE";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TriageRepository triageRepository;
    private final ResultQuestionTriageRepository resultQuestionTriageRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TriageReadService(TriageRepository triageRepository,
                             ResultQuestionTriageRepository resultQuestionTriageRepository,
                             UserRepository userRepository,
                             AuditService auditService) {
        this.triageRepository = triageRepository;
        this.resultQuestionTriageRepository = resultQuestionTriageRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // not readOnly: the audit entry is an INSERT and a read-only transaction would reject it
    // Psychologist: the dependent comes with nickname and real name (the only reader who gets the real name).
    @Transactional
    public PageResponseDTO<TriageReadResponseDTO> list(Long actorId, int page, int size) {
        return listAs(actorId, page, size, TriageReadService::toPsychologistResponse);
    }

    @Transactional
    public TriageReadResponseDTO find(Long actorId, Long triageId) {
        return findAs(actorId, triageId, TriageReadService::toPsychologistResponse);
    }

    // Admin: same triages, but the dependent shows up by nickname only. A separate DTO, not a hidden field.
    @Transactional
    public PageResponseDTO<AdminTriageReadResponseDTO> listForAdmin(Long actorId, int page, int size) {
        return listAs(actorId, page, size, TriageReadService::toAdminResponse);
    }

    @Transactional
    public AdminTriageReadResponseDTO findForAdmin(Long actorId, Long triageId) {
        return findAs(actorId, triageId, TriageReadService::toAdminResponse);
    }

    private <T> PageResponseDTO<T> listAs(Long actorId, int page, int size, TriageMapper<T> mapper) {
        UserType actorRole = actorRole(actorId);
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Triage> triages = triageRepository.findAllBy(pageable);
        Map<Long, List<AnswerResponseDTO>> answers = answersByTriage(triages.getContent());
        triages.forEach(triage -> auditView(actorId, actorRole, triage));
        return PageResponseDTO.of(triages, triage -> mapper.map(triage, answers.getOrDefault(triage.getId(), List.of())));
    }

    private <T> T findAs(Long actorId, Long triageId, TriageMapper<T> mapper) {
        UserType actorRole = actorRole(actorId);
        Triage triage = triageRepository.findWithDependentById(triageId)
                .orElseThrow(() -> new TriageNotFoundException("Triage not found"));
        Map<Long, List<AnswerResponseDTO>> answers = answersByTriage(List.of(triage));
        auditView(actorId, actorRole, triage);
        return mapper.map(triage, answers.getOrDefault(triage.getId(), List.of()));
    }

    @FunctionalInterface
    private interface TriageMapper<T> {
        T map(Triage triage, List<AnswerResponseDTO> answers);
    }

    private UserType actorRole(Long actorId) {
        return userRepository.findById(actorId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"))
                .getTypeUser();
    }

    private void auditView(Long actorId, UserType actorRole, Triage triage) {
        auditService.record(AuditAction.TRIAGE_VIEWED, actorId, actorRole, triage.getDependent().getUserId(),
                AUDIT_ENTITY, triage.getId(), null);
    }

    // a single query loads the answers (and their questions) of every triage in the page
    private Map<Long, List<AnswerResponseDTO>> answersByTriage(List<Triage> triages) {
        if (triages.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = triages.stream().map(Triage::getId).toList();
        Map<Long, List<ResultQuestionTriage>> rowsByTriage = resultQuestionTriageRepository
                .findByTriageIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getTriage().getId()));
        return rowsByTriage.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> toAnswers(entry.getValue())));
    }

    // one entry per question (several rows for multi-choice), in the order of the questionnaire
    private List<AnswerResponseDTO> toAnswers(List<ResultQuestionTriage> rows) {
        Map<QuestionTriage, List<String>> valuesByQuestion = rows.stream()
                .collect(Collectors.groupingBy(ResultQuestionTriage::getQuestion, LinkedHashMap::new,
                        Collectors.mapping(ResultQuestionTriage::getValueResult, Collectors.toList())));
        return valuesByQuestion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(QuestionTriage::getDisplayOrder)
                        .thenComparing(QuestionTriage::getId)))
                .map(entry -> new AnswerResponseDTO(entry.getKey().getId(), entry.getKey().getCode(),
                        entry.getKey().getQuestionText(), entry.getValue()))
                .toList();
    }

    private static TriageReadResponseDTO toPsychologistResponse(Triage triage, List<AnswerResponseDTO> answers) {
        Dependent dependent = triage.getDependent();
        return new TriageReadResponseDTO(triage.getId(), dependent.getUserId(),
                new DependentIdentityDTO(dependent.getNickname(), dependent.getUser().getRealName()),
                triage.getCreatedAt(), answers);
    }

    private static AdminTriageReadResponseDTO toAdminResponse(Triage triage, List<AnswerResponseDTO> answers) {
        Dependent dependent = triage.getDependent();
        return new AdminTriageReadResponseDTO(triage.getId(), dependent.getUserId(),
                new DependentNicknameDTO(dependent.getNickname()), triage.getCreatedAt(), answers);
    }
}
