package plad.exception;

public class InvalidPasswordChangeException extends ApiException {
    public InvalidPasswordChangeException(String message) {
        super(ErrorCode.INVALID_PASSWORD_CHANGE, message);
    }
}
