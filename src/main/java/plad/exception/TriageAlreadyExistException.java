package plad.exception;

public class TriageAlreadyExistException extends ApiException {
    public TriageAlreadyExistException(String message) {
        super(ErrorCode.TRIAGE_ALREADY_EXISTS, message);
    }
}
