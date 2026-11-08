package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.dto.MeetingParticipantResponseDTO;
import plad.dto.MeetingResponseDTO;
import plad.entity.AuditAction;
import plad.entity.Meeting;
import plad.entity.MeetingRegistration;
import plad.entity.MeetingStatus;
import plad.entity.UserType;
import plad.exception.InvalidCredentialsException;
import plad.exception.MeetingConflictException;
import plad.exception.MeetingFullException;
import plad.exception.MeetingNotFoundException;
import plad.repositories.MeetingRegistrationRepository;
import plad.repositories.MeetingRepository;
import plad.repositories.UserRepository;
import plad.services.room.MeetingIdentity;

import java.time.Clock;
import java.util.List;
import java.util.Map;

// Dependents sign up for meetings (decision 24). The triage plays no part: a dependent who never sent it can register
// (decision 25). The route's @PreAuthorize decides the role; ownership of the meeting is checked here.
@Service
public class MeetingRegistrationService {

    private static final String AUDIT_ENTITY = "MEETING";

    private final MeetingRepository meetingRepository;
    private final MeetingRegistrationRepository registrationRepository;
    private final MeetingService meetingService;
    private final PsychologistService psychologistService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MeetingIdentity meetingIdentity;
    private final ConsentService consentService;
    private final Clock clock;

    public MeetingRegistrationService(MeetingRepository meetingRepository,
                                      MeetingRegistrationRepository registrationRepository,
                                      MeetingService meetingService, PsychologistService psychologistService,
                                      UserRepository userRepository, AuditService auditService,
                                      MeetingIdentity meetingIdentity, ConsentService consentService, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.registrationRepository = registrationRepository;
        this.meetingService = meetingService;
        this.psychologistService = psychologistService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.meetingIdentity = meetingIdentity;
        this.consentService = consentService;
        this.clock = clock;
    }

    // Idempotent: registering again changes nothing and answers the same. The meeting row is locked first, so two
    // requests for the last seat run one after the other: the second one counts the seat as taken and gets
    // MEETING_FULL. A dependent who is already in never gets MEETING_FULL, even when the meeting is full.
    // Two consents come before anything else (decisions 35 and 36): the health data one (a registration puts the
    // dependent's name in front of the host, who may then read the triage) and the participation term, whose version
    // the request carries and which is recorded in this same transaction, so a refused registration records nothing.
    @Transactional
    public MeetingResponseDTO register(Long dependentId, Long meetingId, String acceptedParticipationVersion, String ip) {
        consentService.requireHealth(dependentId);
        consentService.requireParticipation(acceptedParticipationVersion);
        Meeting meeting = meetingRepository.findForUpdateById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));
        if (meeting.getStatus() != MeetingStatus.scheduled) {
            throw new MeetingConflictException("Only a scheduled meeting accepts registrations");
        }
        if (!clock.instant().isBefore(meeting.getStartsAt())) {
            throw new MeetingConflictException("Registrations close when the meeting starts");
        }
        if (!registrationRepository.isRegistered(meetingId, dependentId)) {
            if (registrationRepository.countByMeetingId(meetingId) >= meeting.getCapacity()) {
                throw new MeetingFullException("The meeting has no seat left");
            }
            registrationRepository.insertIfAbsent(meetingId, dependentId);
        }
        consentService.recordParticipation(dependentId, ip);
        return meetingService.describe(meeting, dependentId);
    }

    // Idempotent: leaving a meeting one is not in is a no-op. Freeing a seat needs no lock: it can only make room.
    @Transactional
    public void unregister(Long dependentId, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));
        if (!clock.instant().isBefore(meeting.getStartsAt())) {
            throw new MeetingConflictException("A registration cannot be cancelled once the meeting started");
        }
        registrationRepository.deleteRegistration(meetingId, dependentId);
    }

    // Only the psychologist who hosts the meeting, whatever the meeting's state. Not readOnly: every dependent listed
    // gets an audit entry (an INSERT) in this same transaction, so reading twice leaves two entries each (decision 27).
    @Transactional
    public List<MeetingParticipantResponseDTO> participants(Long psychologistId, Long meetingId) {
        psychologistService.requireCanAct(psychologistId);
        meetingService.ownedBy(psychologistId, meetingId);
        UserType actorRole = userRepository.findById(psychologistId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"))
                .getTypeUser();

        List<MeetingRegistration> registrations = registrationRepository.findParticipants(meetingId);
        // ids only: no nickname, no name in the entry (AuditService rejects anything else)
        registrations.forEach(registration -> auditService.record(AuditAction.MEETING_PARTICIPANTS_VIEWED,
                psychologistId, actorRole, registration.getDependent().getUserId(), AUDIT_ENTITY, meetingId,
                Map.of("meeting_id", meetingId)));
        return registrations.stream()
                .map(registration -> new MeetingParticipantResponseDTO(
                        meetingIdentity.of(meetingId, registration.getDependent().getUserId()),
                        registration.getDependent().getNickname(),
                        registration.getDependent().getUser().getRealName(), registration.getRegisteredAt()))
                .toList();
    }
}
