package plad.exception;

public class EmailAlreadyInUseException extends ApiException {
    public EmailAlreadyInUseException(String message) {
        super(ErrorCode.EMAIL_IN_USE, message);
    }
}
