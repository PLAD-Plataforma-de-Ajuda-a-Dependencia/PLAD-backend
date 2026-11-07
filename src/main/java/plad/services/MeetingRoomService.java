package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.config.LiveKitProperties;
import plad.dto.MeetingJoinResponseDTO;
import plad.entity.Dependent;
import plad.entity.Meeting;
import plad.entity.MeetingStatus;
import plad.exception.ForbiddenActionException;
import plad.exception.MeetingNotFoundException;
import plad.exception.MeetingNotOpenException;
import plad.repositories.DependentRepository;
import plad.repositories.MeetingRegistrationRepository;
import plad.repositories.MeetingRepository;
import plad.services.room.MeetingIdentity;
import plad.services.room.MeetingTokenFactory;
import plad.services.room.MeetingTokenFactory.Role;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Entering the live room: the host psychologist or a registered dependent, with the meeting scheduled, from 10 minutes
// before the start until the end. The route's @PreAuthorize does not decide the role here (both may enter); the
// meeting does. Signing the token is local, so nothing here calls LiveKit.
@Service
public class MeetingRoomService {

    private final MeetingRepository meetingRepository;
    private final MeetingRegistrationRepository registrationRepository;
    private final DependentRepository dependentRepository;
    private final PsychologistService psychologistService;
    private final MeetingIdentity meetingIdentity;
    private final MeetingTokenFactory tokenFactory;
    private final LiveKitProperties liveKitProperties;
    private final Clock clock;

    public MeetingRoomService(MeetingRepository meetingRepository, MeetingRegistrationRepository registrationRepository,
                              DependentRepository dependentRepository, PsychologistService psychologistService,
                              MeetingIdentity meetingIdentity, MeetingTokenFactory tokenFactory,
                              LiveKitProperties liveKitProperties, Clock clock) {
        this.meetingRepository = meetingRepository;
        this.registrationRepository = registrationRepository;
        this.dependentRepository = dependentRepository;
        this.psychologistService = psychologistService;
        this.meetingIdentity = meetingIdentity;
        this.tokenFactory = tokenFactory;
        this.liveKitProperties = liveKitProperties;
        this.clock = clock;
    }

    // 404 unknown meeting, then 403 for anyone who is neither the host nor registered (so a stranger learns nothing of
    // the meeting's state), then 409 when the meeting is not open
    @Transactional(readOnly = true)
    public MeetingJoinResponseDTO join(Long userId, Long meetingId) {
        Meeting meeting = meetingRepository.findWithHostById(meetingId)
                .orElseThrow(() -> new MeetingNotFoundException("Meeting not found"));

        Role role;
        String displayName;
        if (meeting.getPsychologist().getUserId().equals(userId)) {
            psychologistService.requireCanAct(userId);
            role = Role.psicologo;
            displayName = meeting.getPsychologist().getUser().getRealName(); // the host, as the calendar shows it
        } else if (registrationRepository.isRegistered(meetingId, userId)) {
            role = Role.dependente;
            displayName = dependentRepository.findById(userId).map(Dependent::getNickname)
                    .orElseThrow(() -> new ForbiddenActionException("Only the host or a registered dependent can enter"));
        } else {
            throw new ForbiddenActionException("Only the host or a registered dependent can enter");
        }

        Instant now = clock.instant();
        if (meeting.getStatus() != MeetingStatus.scheduled
                || !MeetingRules.joinWindowOpen(meeting.getStartsAt(), meeting.getEndsAt(), now)) {
            throw new MeetingNotOpenException("The meeting can be entered while it is scheduled, from 10 minutes "
                    + "before it starts until it ends");
        }

        Duration validFor = Duration.between(now, meeting.getEndsAt().plus(MeetingRules.TOKEN_GRACE));
        String token = tokenFactory.create(meetingId, meetingIdentity.of(meetingId, userId), displayName, role, validFor);
        return new MeetingJoinResponseDTO(liveKitProperties.url(), token);
    }
}
