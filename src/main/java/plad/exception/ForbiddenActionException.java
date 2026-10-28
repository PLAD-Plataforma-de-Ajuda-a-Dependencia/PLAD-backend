package plad.exception;

public class ForbiddenActionException extends ApiException {
    public ForbiddenActionException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
