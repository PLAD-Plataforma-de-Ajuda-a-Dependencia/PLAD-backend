package plad.services.room;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.ServerError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import plad.config.LiveKitProperties;
import plad.exception.MeetingParticipantNotFoundException;
import plad.exception.RoomUnavailableException;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// The real gateway, over LiveKit's room service. Every call has a deadline: a slow provider must turn into a 503 the
// client can retry, not into a request that hangs while it holds a database transaction. (The deadline is enforced here
// by cancelling the call; OkHttp's own timeouts cannot be tuned without declaring OkHttp as a dependency of ours.)
// Logs say what was asked and what came back (status and error code); never the URL, the credentials, a token or the
// identity.
@Component
public class LiveKitMeetingRoomGateway implements MeetingRoomGateway {

    private static final Logger log = LoggerFactory.getLogger(LiveKitMeetingRoomGateway.class);
    private static final String NOT_FOUND = "not_found";
    static final Duration DEADLINE = Duration.ofSeconds(6);

    private final RoomServiceClient client;
    private final Duration deadline;
    // one virtual thread per call: the caller waits on the future with the deadline, the thread just blocks on I/O
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // building the client opens no connection: the first call does
    @Autowired
    public LiveKitMeetingRoomGateway(LiveKitProperties properties) {
        this(RoomServiceClient.createClient(properties.url(), properties.apiKey(), properties.apiSecret()), DEADLINE);
    }

    LiveKitMeetingRoomGateway(RoomServiceClient client, Duration deadline) {
        this.client = client;
        this.deadline = deadline;
    }

    @Override
    public void setSpeakPermission(String room, String identity, boolean allowed) {
        execute("update participant", client.updateParticipant(room, identity, null, null,
                RoomPermissions.speaking(allowed)), false);
    }

    @Override
    public void removeParticipant(String room, String identity) {
        // RemoveParticipant only disconnects the person: LiveKit does NOT invalidate the token they hold, so they could
        // connect again with it until it expires. What keeps them out is (a) the short life of our tokens
        // (MeetingRules#TOKEN_TTL, 2 min, which LiveKit checks only on a first connection) and (b) POST /join, the only
        // way to a new token, answering 403 once their registration is gone (MeetingModerationService#remove).
        execute("remove participant", client.removeParticipant(room, identity), false);
    }

    @Override
    public void closeRoom(String room) {
        execute("delete room", client.deleteRoom(room), true);
    }

    private void execute(String action, Call<?> call, boolean notFoundIsFine) {
        Response<?> response = send(action, call);
        if (response.isSuccessful()) {
            return;
        }
        ServerError error = ServerError.from(response);
        String code = error == null ? null : error.getCode();
        if (NOT_FOUND.equals(code) || response.code() == 404) {
            if (notFoundIsFine) {
                return;
            }
            throw new MeetingParticipantNotFoundException("That participant is not in the room");
        }
        log.warn("LiveKit {} refused: http={} code={}", action, response.code(), code);
        throw new RoomUnavailableException("The video provider refused the request");
    }

    private Response<?> send(String action, Call<?> call) {
        Future<Response<?>> pending = executor.submit(call::execute);
        try {
            return pending.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel();
            pending.cancel(true);
            log.warn("LiveKit {} gave no answer within {} ms", action, deadline.toMillis());
            throw new RoomUnavailableException("The video provider did not answer in time");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                log.warn("LiveKit {} failed before an answer ({})", action, cause.getClass().getSimpleName());
                throw new RoomUnavailableException("The video provider did not answer");
            }
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.cancel();
            throw new RoomUnavailableException("The call to the video provider was interrupted");
        }
    }
}
