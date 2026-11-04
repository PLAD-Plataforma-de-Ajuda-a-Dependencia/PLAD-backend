package plad.exception;

public class ConsentRequiredException extends ApiException {
    public ConsentRequiredException(String message) {
        super(ErrorCode.CONSENT_REQUIRED, message);
    }
}
