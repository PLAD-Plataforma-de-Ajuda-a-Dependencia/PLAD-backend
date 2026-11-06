package plad.exception;

// an edit would leave fewer seats than there are registered dependents
public class MeetingCapacityTooLowException extends ApiException {
    public MeetingCapacityTooLowException(String message) {
        super(ErrorCode.MEETING_CAPACITY_TOO_LOW, message);
    }
}
