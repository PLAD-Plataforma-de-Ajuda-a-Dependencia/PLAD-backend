package plad.exception;

// overlap with another scheduled meeting of the same psychologist, or a state/time that does not allow the action
public class MeetingConflictException extends ApiException {
    public MeetingConflictException(String message) {
        super(ErrorCode.MEETING_CONFLICT, message);
    }
}
