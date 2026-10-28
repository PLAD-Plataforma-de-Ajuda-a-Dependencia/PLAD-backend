package plad.exception;

public class InvalidRegistrationException extends ApiException {
    public InvalidRegistrationException(String message) {
        super(ErrorCode.INVALID_REGISTRATION, message);
    }
}
