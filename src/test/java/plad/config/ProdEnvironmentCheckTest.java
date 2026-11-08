package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdEnvironmentCheckTest {

    private final ProdEnvironmentCheck check = new ProdEnvironmentCheck();

    @Test
    void prodWithoutDatabaseVariablesStopsAndNamesEveryMissingOne() {
        MockEnvironment env = new MockEnvironment().withProperty("DB_USER", "app");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageNotContaining("DB_USER");
    }

    @Test
    void prodTreatsABlankVariableAsMissing() {
        MockEnvironment env = withEveryVariable().withProperty("DB_PASSWORD", "  ");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(env, null))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void prodWithAllVariablesStarts() {
        MockEnvironment env = withEveryVariable();
        env.setActiveProfiles("prod");

        assertThatCode(() -> check.postProcessEnvironment(env, null)).doesNotThrowAnyException();
    }

    // the live room has no default in prod: LiveKit's address and credentials, and the key of the participant identities
    @Test
    void prodWithoutTheLiveRoomVariablesStopsAndNamesEveryOne() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db/x")
                .withProperty("DB_USER", "app")
                .withProperty("DB_PASSWORD", "secret")
                .withProperty("LIVEKIT_API_KEY", "key");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVEKIT_URL")
                .hasMessageContaining("LIVEKIT_API_SECRET")
                .hasMessageContaining("MEETING_IDENTITY_SECRET")
                .hasMessageNotContaining("LIVEKIT_API_KEY")
                .hasMessageNotContaining("DB_URL");
    }

    @Test
    void prodTreatsABlankIdentitySecretAsMissing() {
        MockEnvironment env = withEveryVariable().withProperty("MEETING_IDENTITY_SECRET", " ");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(env, null))
                .hasMessageContaining("MEETING_IDENTITY_SECRET");
    }

    private static MockEnvironment withEveryVariable() {
        return new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db/x")
                .withProperty("DB_USER", "app")
                .withProperty("DB_PASSWORD", "secret")
                .withProperty("LIVEKIT_URL", "wss://plad.livekit.cloud")
                .withProperty("LIVEKIT_API_KEY", "key")
                .withProperty("LIVEKIT_API_SECRET", "secret")
                .withProperty("MEETING_IDENTITY_SECRET", "identity");
    }

    @Test
    void otherProfilesKeepTheLocalDefaults() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> check.postProcessEnvironment(dev, null)).doesNotThrowAnyException();
        assertThatCode(() -> check.postProcessEnvironment(new MockEnvironment(), null)).doesNotThrowAnyException();
    }
}
