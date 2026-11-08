package plad.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.Meeting;
import plad.entity.MeetingStatus;
import plad.exception.MeetingNotOpenException;
import plad.exception.MeetingParticipantNotFoundException;
import plad.repositories.MeetingRegistrationRepository;
import plad.services.room.MeetingIdentity;
import plad.services.room.MeetingRoomGateway;
import plad.services.room.MeetingRooms;

// Moderation of the live room (RF006), host only. The host names a participant by the opaque identity the room shows;
// it is matched against the dependents registered in THIS meeting before anything is done, so an identity from
// another meeting, the host's own, or a made-up one never reaches the video provider.
@Service
public class MeetingModerationService {

    private final MeetingService meetingService;
    private final PsychologistService psychologistService;
    private final MeetingRegistrationRepository registrationRepository;
    private final MeetingIdentity meetingIdentity;
    private final MeetingRoomGateway roomGateway;

    public MeetingModerationService(MeetingService meetingService, PsychologistService psychologistService,
                                    MeetingRegistrationRepository registrationRepository,
                                    MeetingIdentity meetingIdentity, MeetingRoomGateway roomGateway) {
        this.meetingService = meetingService;
        this.psychologistService = psychologistService;
        this.registrationRepository = registrationRepository;
        this.meetingIdentity = meetingIdentity;
        this.roomGateway = roomGateway;
    }

    // allow or take away audio and video of one participant; 404 when they are not connected to the room
    @Transactional(readOnly = true)
    public void setSpeech(Long hostId, Long meetingId, String identity, boolean allowed) {
        authorize(hostId, meetingId, identity);
        roomGateway.setSpeakPermission(MeetingRooms.name(meetingId), identity, allowed);
    }

    // Drops the participant and takes their registration away, so POST /join answers 403 from now on and the seat is
    // free. The provider goes first: if it fails nothing changed here and the host can try again. Someone who is
    // registered but not connected is still removed from the meeting.
    @Transactional
    public void remove(Long hostId, Long meetingId, String identity) {
        Long dependentId = authorize(hostId, meetingId, identity);
        try {
            roomGateway.removeParticipant(MeetingRooms.name(meetingId), identity);
        } catch (MeetingParticipantNotFoundException notConnected) {
            // nothing to drop from the room
        }
        registrationRepository.deleteRegistration(meetingId, dependentId);
    }

    // 403 not the host, 404 no such meeting; then 409 when it is not scheduled; then 404 when the identity is not a
    // dependent registered in it. Returns that dependent's id.
    private Long authorize(Long hostId, Long meetingId, String identity) {
        psychologistService.requireCanAct(hostId);
        Meeting meeting = meetingService.ownedBy(hostId, meetingId);
        if (meeting.getStatus() != MeetingStatus.scheduled) {
            throw new MeetingNotOpenException("Only a scheduled meeting can be moderated");
        }
        return registrationRepository.findDependentIds(meetingId).stream()
                .filter(dependentId -> meetingIdentity.matches(identity, meetingId, dependentId))
                .findFirst()
                .orElseThrow(() -> new MeetingParticipantNotFoundException("No such participant in this meeting"));
    }
}
