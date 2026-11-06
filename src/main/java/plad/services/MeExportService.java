package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.AnswerResponseDTO;
import plad.dto.MeExportAccountDTO;
import plad.dto.MeExportConsentDTO;
import plad.dto.MeExportDependentDTO;
import plad.dto.MeExportMeetingRegistrationDTO;
import plad.dto.MeExportNicknameDTO;
import plad.dto.MeExportPsychologistDTO;
import plad.dto.MeExportResponseDTO;
import plad.dto.MeExportTriageDTO;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.ResultQuestionTriage;
import plad.entity.Triage;
import plad.entity.User;
import plad.exception.InvalidCredentialsException;
import plad.repositories.ConsentRepository;
import plad.repositories.DependentNicknameHistoryRepository;
import plad.repositories.DependentRepository;
import plad.repositories.MeetingRegistrationRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.ResultQuestionTriageRepository;
import plad.repositories.TriageRepository;
import plad.repositories.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Copy of the data the platform holds about the user (LGPD art. 18, II and V). The user comes from the token and only
// their own rows are read. The subject reading their own data is not a third-party access, so nothing goes to audit_log
// (same as GET /me). A dependent's real name is not exported (nickname only); the psychologist's own name is.
@Service
public class MeExportService {

    private final UserRepository userRepository;
    private final DependentRepository dependentRepository;
    private final PsychologistRepository psychologistRepository;
    private final DependentNicknameHistoryRepository nicknameHistoryRepository;
    private final ConsentRepository consentRepository;
    private final TriageRepository triageRepository;
    private final ResultQuestionTriageRepository resultQuestionTriageRepository;
    private final MeetingRegistrationRepository meetingRegistrationRepository;
    private final Clock clock;

    public MeExportService(UserRepository userRepository, DependentRepository dependentRepository,
                           PsychologistRepository psychologistRepository,
                           DependentNicknameHistoryRepository nicknameHistoryRepository,
                           ConsentRepository consentRepository, TriageRepository triageRepository,
                           ResultQuestionTriageRepository resultQuestionTriageRepository,
                           MeetingRegistrationRepository meetingRegistrationRepository, Clock clock) {
        this.userRepository = userRepository;
        this.dependentRepository = dependentRepository;
        this.psychologistRepository = psychologistRepository;
        this.nicknameHistoryRepository = nicknameHistoryRepository;
        this.consentRepository = consentRepository;
        this.triageRepository = triageRepository;
        this.resultQuestionTriageRepository = resultQuestionTriageRepository;
        this.meetingRegistrationRepository = meetingRegistrationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MeExportResponseDTO export(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

        MeExportAccountDTO account = new MeExportAccountDTO(user.getId(), user.getEmail(), user.getTypeUser(),
                user.getBirthDate(), user.getGender(), user.getCreatedAt());
        List<MeExportConsentDTO> consents = consentRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(c -> new MeExportConsentDTO(c.getDocument(), c.getVersion(), c.getAcceptedAt(), c.getIp()))
                .toList();

        MeExportDependentDTO dependent = null;
        MeExportPsychologistDTO psychologist = null;
        MeExportTriageDTO triage = null;
        switch (user.getTypeUser()) {
            case dependente -> {
                dependent = exportDependent(userId);
                triage = exportTriage(userId).orElse(null);
            }
            case psicologo -> psychologist = exportPsychologist(user);
            case admin -> {
                // only the common block and the consents
            }
        }
        return new MeExportResponseDTO(Instant.now(clock), account, dependent, psychologist, consents, triage);
    }

    private MeExportDependentDTO exportDependent(Long userId) {
        Dependent dependent = dependentRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Dependent " + userId + " has no dependents row"));
        List<MeExportNicknameDTO> history = nicknameHistoryRepository.findByDependentIdOrderByAssignedAtAscIdAsc(userId).stream()
                .map(h -> new MeExportNicknameDTO(h.getNickname(), h.getAssignedAt(), h.getReplacedAt()))
                .toList();
        List<MeExportMeetingRegistrationDTO> registrations = meetingRegistrationRepository.findByDependent(userId).stream()
                .map(r -> new MeExportMeetingRegistrationDTO(r.getMeeting().getId(), r.getMeeting().getTitle(),
                        r.getMeeting().getStartsAt(), r.getRegisteredAt()))
                .toList();
        return new MeExportDependentDTO(dependent.getNickname(), dependent.getCreatedAt(), history, registrations);
    }

    private MeExportPsychologistDTO exportPsychologist(User user) {
        Psychologist psychologist = psychologistRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Psychologist " + user.getId() + " has no psychologists row"));
        return new MeExportPsychologistDTO(user.getRealName(), psychologist.getCredentialType(),
                psychologist.getCrmCrpNumber(), psychologist.getGraduate(), psychologist.isValidate(),
                psychologist.getValidateTime());
    }

    private Optional<MeExportTriageDTO> exportTriage(Long dependentId) {
        return triageRepository.findByDependentUserId(dependentId).map(triage -> new MeExportTriageDTO(
                triage.getId(), triage.getCreatedAt(), triage.getUpdatedAt(), answersOf(triage)));
    }

    // one entry per question (several rows for multi-choice), in the order of the questionnaire
    private List<AnswerResponseDTO> answersOf(Triage triage) {
        List<ResultQuestionTriage> rows = resultQuestionTriageRepository.findByTriageIdInOrderByIdAsc(List.of(triage.getId()));
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
}
