package plad.exception;

// the video provider did not answer or refused the call: the client can try again shortly
public class RoomUnavailableException extends ApiException {
    public RoomUnavailableException(String message) {
        super(ErrorCode.ROOM_UNAVAILABLE, message);
    }
}
