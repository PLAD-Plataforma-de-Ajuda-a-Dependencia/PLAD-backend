package plad.services.room;

import livekit.LivekitModels;
import livekit.LivekitModels.ParticipantPermission;
import livekit.LivekitModels.TrackSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// LiveKit's UpdateParticipant replaces the whole permission: every call must repeat what has to stay. The raised hand
// is a data message (the chat's permission), so a dependent never needs to update their own metadata.
class RoomPermissionsTest {

    @Test
    void allowingGivesAudioAndVideoButNeverTheScreen() {
        ParticipantPermission permission = RoomPermissions.speaking(true);

        assertThat(permission.getCanPublish()).isTrue();
        assertThat(permission.getCanPublishSourcesList()).containsExactlyInAnyOrder(TrackSource.MICROPHONE, TrackSource.CAMERA);
        assertThat(permission.getCanPublishSourcesList()).doesNotContain(TrackSource.SCREEN_SHARE,
                TrackSource.SCREEN_SHARE_AUDIO);
    }

    @Test
    void takingAwayStopsPublishingAndKeepsListeningAndTheChat() {
        ParticipantPermission permission = RoomPermissions.speaking(false);

        assertThat(permission.getCanPublish()).isFalse();
        assertThat(permission.getCanPublishSourcesList()).isEmpty();
        assertThat(permission.getCanSubscribe()).isTrue();
        assertThat(permission.getCanPublishData()).isTrue(); // the chat and the raised hand
    }

    @Test
    void allowingAlsoKeepsListeningAndTheChat() {
        ParticipantPermission permission = RoomPermissions.speaking(true);

        assertThat(permission.getCanSubscribe()).isTrue();
        assertThat(permission.getCanPublishData()).isTrue();
    }

    @Test
    void neitherCallLetsTheDependentUpdateTheirOwnMetadata() {
        assertThat(RoomPermissions.speaking(true).getCanUpdateMetadata()).isFalse();
        assertThat(RoomPermissions.speaking(false).getCanUpdateMetadata()).isFalse();
    }
}
