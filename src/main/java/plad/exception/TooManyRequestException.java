package plad.exception;

public class TooManyRequestException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestException(String message, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
