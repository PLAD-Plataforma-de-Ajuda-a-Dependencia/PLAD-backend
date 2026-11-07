package plad.exception;

// the identity is not a dependent registered in this meeting (or is no longer in the room)
public class MeetingParticipantNotFoundException extends ApiException {
    public MeetingParticipantNotFoundException(String message) {
        super(ErrorCode.MEETING_PARTICIPANT_NOT_FOUND, message);
    }
}
