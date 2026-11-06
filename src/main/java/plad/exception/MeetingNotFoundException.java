package plad.exception;

public class MeetingNotFoundException extends ApiException {
    public MeetingNotFoundException(String message) {
        super(ErrorCode.MEETING_NOT_FOUND, message);
    }
}
