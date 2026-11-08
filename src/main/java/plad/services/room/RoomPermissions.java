package plad.services.room;

import livekit.LivekitModels;

import java.util.List;

// The permission object sent by "allow / take away the speech". LiveKit's UpdateParticipant REPLACES the participant's
// whole permission, so every call has to repeat what must stay: subscribing and the chat (data, which also carries the
// raised hand, a data message to the host). Updating their own metadata stays off, as in the join token. Sending only
// canPublish would silently switch the other two off.
public final class RoomPermissions {

    private RoomPermissions() {
    }

    public static LivekitModels.ParticipantPermission speaking(boolean allowed) {
        LivekitModels.ParticipantPermission.Builder permission = LivekitModels.ParticipantPermission.newBuilder()
                .setCanSubscribe(true)
                .setCanPublishData(true)
                .setCanUpdateMetadata(false)
                .setCanPublish(allowed);
        if (allowed) {
            // audio and video; the screen stays with the psychologist
            permission.addAllCanPublishSources(List.of(LivekitModels.TrackSource.MICROPHONE,
                    LivekitModels.TrackSource.CAMERA));
        }
        return permission.build();
    }
}
