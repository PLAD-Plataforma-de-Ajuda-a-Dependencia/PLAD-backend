package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// The test fixtures hard-code the versions in force. Raising a version in application.properties must fail here first,
// with a clear reason, and not as dozens of 400 CONSENT_REQUIRED in unrelated tests.
@SpringBootTest
@Import(TestcontainersConfig.class)
class ConsentVersionsTest {

    @Value("${consent.health-version}")
    private String healthVersion;
    @Value("${consent.participation-version}")
    private String participationVersion;

    @Test
    void theFixturesFollowTheVersionsInForce() {
        assertThat(TestConsents.HEALTH_VERSION).isEqualTo(healthVersion);
        assertThat(TestConsents.PARTICIPATION_VERSION).isEqualTo(participationVersion);
    }
}
