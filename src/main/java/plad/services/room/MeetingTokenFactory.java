package plad.services.room;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanUpdateOwnMetadata;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.stereotype.Component;
import plad.config.LiveKitProperties;

import java.time.Duration;
import java.util.List;

// Signs the token a browser uses to enter the room. Local arithmetic, no network call. What the token says is what
// LiveKit (the provider) knows about the person: a room, an opaque identity, a display name (the dependent's nickname,
// or the host's name, which every user already sees in the calendar) and the role. Nothing else: no e-mail, no user
// id, no real name of a dependent.
@Component
public class MeetingTokenFactory {

    public enum Role {
        psicologo, dependente
    }

    // what the host may publish; a dependent starts with none until the host allows speaking
    private static final List<String> HOST_SOURCES = List.of("camera", "microphone", "screen_share", "screen_share_audio");

    private final LiveKitProperties properties;

    public MeetingTokenFactory(LiveKitProperties properties) {
        this.properties = properties;
    }

    public String create(long meetingId, String identity, String displayName, Role role, Duration validFor) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        token.setMetadata("{\"role\":\"" + role.name() + "\"}");
        token.setTtl(validFor.toMillis());
        token.addGrants(new RoomJoin(true), new RoomName(MeetingRooms.name(meetingId)),
                new CanSubscribe(true), new CanPublishData(true)); // listen, and the chat
        if (role == Role.psicologo) {
            token.addGrants(new CanPublish(true), new CanPublishSources(HOST_SOURCES), new RoomAdmin(true));
        } else {
            // no canPublish: neither microphone nor camera until the host allows it. canUpdateOwnMetadata is for the
            // raised hand (a participant attribute); LiveKit has no narrower permission, so a dependent could also
            // rename themselves in the room: nothing here trusts the name or the metadata of a participant
            token.addGrants(new CanPublish(false), new CanUpdateOwnMetadata(true));
        }
        return token.toJwt();
    }
}
