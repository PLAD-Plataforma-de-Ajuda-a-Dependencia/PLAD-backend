package plad.exception;

public class InvalidTriageException extends ApiException {
    public InvalidTriageException(String message) {
        super(ErrorCode.INVALID_TRIAGE, message);
    }
}
