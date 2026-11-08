package plad.services.room;

import org.junit.jupiter.api.Test;
import plad.config.LiveKitProperties;

import static org.assertj.core.api.Assertions.assertThat;

// The opaque name a person has in a meeting's video room: stable per person and meeting, different everywhere else, and
// useless without MEETING_IDENTITY_SECRET.
class MeetingIdentityTest {

    private static final String SECRET = "unit-test-identity-secret-0123456789-0123456789";

    private final MeetingIdentity identity = new MeetingIdentity(properties(SECRET));

    @Test
    void theSamePersonInTheSameMeetingAlwaysGetsTheSameIdentity() {
        assertThat(identity.of(7, 42)).isEqualTo(identity.of(7, 42));
    }

    @Test
    void anotherPersonOrAnotherMeetingGetsAnotherIdentity() {
        assertThat(identity.of(7, 42)).isNotEqualTo(identity.of(7, 43));
        assertThat(identity.of(7, 42)).isNotEqualTo(identity.of(8, 42));
        // (7, 42) and (74, 2) would collide if the two numbers were glued without a separator
        assertThat(identity.of(7, 42)).isNotEqualTo(identity.of(74, 2));
    }

    @Test
    void theIdentityIsUrlSafeLongAndHoldsNeitherIdNorSecret() {
        String value = identity.of(7, 42);

        assertThat(value).matches("[A-Za-z0-9_-]{43}"); // 32 bytes, base64url, no padding
        assertThat(value).doesNotContain("42").doesNotContain(SECRET);
    }

    @Test
    void anotherSecretGivesTheSamePersonAnotherIdentity() {
        MeetingIdentity other = new MeetingIdentity(properties("another-unit-test-secret-0123456789-0123456789"));

        assertThat(other.of(7, 42)).isNotEqualTo(identity.of(7, 42));
    }

    @Test
    void matchesAcceptsOnlyTheRightIdentityForThatMeetingAndPerson() {
        String value = identity.of(7, 42);

        assertThat(identity.matches(value, 7, 42)).isTrue();
        assertThat(identity.matches(value, 7, 43)).isFalse();
        assertThat(identity.matches(value, 8, 42)).isFalse();
        assertThat(identity.matches(null, 7, 42)).isFalse();
        assertThat(identity.matches("", 7, 42)).isFalse();
        assertThat(identity.matches(value.substring(1), 7, 42)).isFalse();
    }

    @Test
    void theRoomNameCarriesTheMeetingIdOnly() {
        assertThat(MeetingRooms.name(7)).isEqualTo("plad-meeting-7");
    }

    private static LiveKitProperties properties(String identitySecret) {
        return new LiveKitProperties("ws://localhost:7880", "key", "api-secret-0123456789-0123456789-0123456789", identitySecret);
    }
}
