package plad.exception;

// the meeting has no seat left; a dependent who is already registered never gets this (registering again is a no-op)
public class MeetingFullException extends ApiException {
    public MeetingFullException(String message) {
        super(ErrorCode.MEETING_FULL, message);
    }
}
