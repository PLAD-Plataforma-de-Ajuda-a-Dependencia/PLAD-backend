package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// prod must not start with a credential nobody chose: DB_URL, DB_USER and DB_PASSWORD have no default,
// so a missing variable stops the app (like JWT_SECRET). Plain file check, no Spring context.
class ProdProfilePropertiesTest {

    private final Properties prod = load("application-prod.properties");

    @Test
    void databaseSettingsAreRequiredPlaceholdersWithoutDefault() {
        assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(prod.getProperty("spring.datasource.username")).isEqualTo("${DB_USER}");
        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    // the live room: no default for the address, the credentials or the key of the participant identities
    @Test
    void liveRoomSettingsAreRequiredPlaceholdersWithoutDefault() {
        assertThat(prod.getProperty("livekit.url")).isEqualTo("${LIVEKIT_URL}");
        assertThat(prod.getProperty("livekit.api-key")).isEqualTo("${LIVEKIT_API_KEY}");
        assertThat(prod.getProperty("livekit.api-secret")).isEqualTo("${LIVEKIT_API_SECRET}");
        assertThat(prod.getProperty("meeting.identity-secret")).isEqualTo("${MEETING_IDENTITY_SECRET}");
    }

    // and the shared file has no default for the secrets either (only the local address of livekit-server --dev)
    @Test
    void sharedFileGivesNoDefaultToTheLiveRoomCredentials() {
        Properties shared = load("application.properties");
        assertThat(shared.getProperty("livekit.api-key")).isEqualTo("${LIVEKIT_API_KEY}");
        assertThat(shared.getProperty("livekit.api-secret")).isEqualTo("${LIVEKIT_API_SECRET}");
        assertThat(shared.getProperty("meeting.identity-secret")).isEqualTo("${MEETING_IDENTITY_SECRET}");
    }

    @Test
    void apiDocsAreOff() {
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    }

    @Test
    void devProfileOpensSwaggerAndDebugsOurPackage() {
        Properties dev = load("application-dev.properties");
        assertThat(dev.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThat(dev.getProperty("logging.level.plad")).isEqualTo("DEBUG");
    }

    private static Properties load(String name) {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource(name));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
