package plad.exception;

public class TooManyRequestException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
