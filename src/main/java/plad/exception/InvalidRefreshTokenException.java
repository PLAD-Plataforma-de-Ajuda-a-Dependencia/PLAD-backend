package plad.exception;

// missing, unknown, expired or already used refresh token; the client must log in again
public class InvalidRefreshTokenException extends ApiException {
    public InvalidRefreshTokenException(String message) {
        super(ErrorCode.INVALID_REFRESH_TOKEN, message);
    }
}
