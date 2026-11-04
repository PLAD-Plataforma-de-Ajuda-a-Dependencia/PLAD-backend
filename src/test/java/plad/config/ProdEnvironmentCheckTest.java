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
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db/x")
                .withProperty("DB_USER", "app")
                .withProperty("DB_PASSWORD", "  ");
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> check.postProcessEnvironment(env, null))
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void prodWithAllVariablesStarts() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db/x")
                .withProperty("DB_USER", "app")
                .withProperty("DB_PASSWORD", "secret");
        env.setActiveProfiles("prod");

        assertThatCode(() -> check.postProcessEnvironment(env, null)).doesNotThrowAnyException();
    }

    @Test
    void otherProfilesKeepTheLocalDefaults() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> check.postProcessEnvironment(dev, null)).doesNotThrowAnyException();
        assertThatCode(() -> check.postProcessEnvironment(new MockEnvironment(), null)).doesNotThrowAnyException();
    }
}
