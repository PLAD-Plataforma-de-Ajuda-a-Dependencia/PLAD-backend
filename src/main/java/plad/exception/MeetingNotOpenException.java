package plad.exception;

// the live room is not open to this action: the meeting is not scheduled, or it is outside the join window
public class MeetingNotOpenException extends ApiException {
    public MeetingNotOpenException(String message) {
        super(ErrorCode.MEETING_NOT_OPEN, message);
    }
}
