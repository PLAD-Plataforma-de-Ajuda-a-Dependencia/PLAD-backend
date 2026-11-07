package plad.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Like JWT_SECRET: the live room does not start with a missing, short or example value, and the messages never carry one.
class LiveKitPropertiesTest {

    private static final String STRONG = "0123456789-0123456789-0123456789-0123456789";
    private static final String OTHER_STRONG = "abcdefghij-abcdefghij-abcdefghij-abcdefghij";

    @Test
    void validSettingsStartInDevelopmentWithAPlainOrSecureAddress() {
        assertThatCode(() -> LiveKitProperties.validated("ws://localhost:7880", "key", STRONG, OTHER_STRONG, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> LiveKitProperties.validated("wss://plad.livekit.cloud", "key", STRONG, OTHER_STRONG, false))
                .doesNotThrowAnyException();
    }

    @Test
    void productionNeedsASecureAddress() {
        assertThatCode(() -> LiveKitProperties.validated("wss://plad.livekit.cloud", "key", STRONG, OTHER_STRONG, true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> LiveKitProperties.validated("ws://plad.example", "key", STRONG, OTHER_STRONG, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LIVEKIT_URL");
    }

    @Test
    void aMissingOrMalformedAddressStops() {
        for (String url : new String[]{null, "", "  ", "http://localhost:7880", "localhost:7880", "ws://", "wss://"}) {
            assertThatThrownBy(() -> LiveKitProperties.validated(url, "key", STRONG, OTHER_STRONG, false))
                    .as("url=%s", url).isInstanceOf(IllegalStateException.class).hasMessageContaining("LIVEKIT_URL");
        }
    }

    @Test
    void aMissingOrExampleApiKeyStops() {
        for (String key : new String[]{null, "", "  ", "troque-por-uma-chave"}) {
            assertThatThrownBy(() -> LiveKitProperties.validated("ws://localhost:7880", key, STRONG, OTHER_STRONG, false))
                    .as("key=%s", key).isInstanceOf(IllegalStateException.class).hasMessageContaining("LIVEKIT_API_KEY");
        }
    }

    @Test
    void aWeakApiSecretStopsAndNamesIt() {
        for (String secret : new String[]{null, "", "  ", "short", "x".repeat(31), "troque-por-saida-de-openssl-rand-base64-48"}) {
            assertThatThrownBy(() -> LiveKitProperties.validated("ws://localhost:7880", "key", secret, OTHER_STRONG, false))
                    .as("secret=%s", secret).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LIVEKIT_API_SECRET");
        }
    }

    @Test
    void aWeakIdentitySecretStopsAndNamesIt() {
        for (String secret : new String[]{null, "", "  ", "short", "x".repeat(31), "troque-por-saida-de-openssl-rand-base64-48"}) {
            assertThatThrownBy(() -> LiveKitProperties.validated("ws://localhost:7880", "key", STRONG, secret, false))
                    .as("secret=%s", secret).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MEETING_IDENTITY_SECRET");
        }
    }

    @Test
    void thirtyTwoBytesIsEnoughAndTheLengthIsCountedInBytes() {
        assertThatCode(() -> LiveKitProperties.validated("ws://localhost:7880", "key", "x".repeat(32), OTHER_STRONG, false))
                .doesNotThrowAnyException();
        // 16 characters of 2 bytes each are 32 bytes
        assertThatCode(() -> LiveKitProperties.validated("ws://localhost:7880", "key", "é".repeat(16), OTHER_STRONG, false))
                .doesNotThrowAnyException();
    }

    @Test
    void neitherTheMessagesNorToStringCarryAConfiguredValue() {
        String weakSecret = "weak-secret-value";
        assertThatThrownBy(() -> LiveKitProperties.validated("ws://localhost:7880", "my-api-key", weakSecret, OTHER_STRONG, false))
                .hasMessageNotContaining(weakSecret).hasMessageNotContaining("my-api-key");

        String printed = LiveKitProperties.validated("ws://localhost:7880", "my-api-key", STRONG, OTHER_STRONG, false).toString();
        assertThat(printed).doesNotContain(STRONG).doesNotContain(OTHER_STRONG).doesNotContain("my-api-key");
    }
}
