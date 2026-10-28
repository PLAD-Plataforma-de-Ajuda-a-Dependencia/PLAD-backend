package plad.exception;

public class TriageNotFoundException extends ApiException {
    public TriageNotFoundException(String message) {
        super(ErrorCode.TRIAGE_NOT_FOUND, message);
    }
}
