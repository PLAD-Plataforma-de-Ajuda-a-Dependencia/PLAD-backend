package plad.services.room;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Stands in for LiveKit in every Spring test (see FakeRoomGatewayConfig): it records what the application asked and
// can be told to fail, so the tests never reach a real video provider.
public class FakeMeetingRoomGateway implements MeetingRoomGateway {

    public record Call(String action, String room, String identity, Boolean allowed) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failure;

    // the context is shared by many test classes: every test that looks at the calls starts from a clean slate
    public void reset() {
        calls.clear();
        failure = null;
    }

    // the next calls are recorded and then fail with this exception
    public void failWith(RuntimeException exception) {
        this.failure = exception;
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    @Override
    public void setSpeakPermission(String room, String identity, boolean allowed) {
        record(new Call("speak", room, identity, allowed));
    }

    @Override
    public void removeParticipant(String room, String identity) {
        record(new Call("remove", room, identity, null));
    }

    @Override
    public void closeRoom(String room) {
        record(new Call("close", room, null, null));
    }

    private void record(Call call) {
        calls.add(call);
        if (failure != null) {
            throw failure;
        }
    }
}
