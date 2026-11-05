package plad.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import plad.services.JwtService;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

// Wires only JwtService with the property resolution of a real context, so no extra Spring context (and Hikari pool) is opened.
class JwtSecretStartupTest {

    private static final String OLD_EXAMPLE_SECRET = "troque-por-saida-de-openssl-rand-base64-48";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JwtService.class)
            .withPropertyValues("jwt.expiration-ms=60000");

    @Test
    void contextDoesNotStartWithAnEmptySecret() {
        runner.withPropertyValues("jwt.secret=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET")
                    .hasMessageContaining("openssl rand -base64 48");
        });
    }

    @Test
    void contextDoesNotStartWith31BytesAndTheMessageHasNoSecret() {
        String secret = randomText(31);

        runner.withPropertyValues("jwt.secret=" + secret).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET")
                    .hasMessageNotContaining(secret);
            assertThat(fullMessages(context.getStartupFailure())).doesNotContain(secret);
        });
    }

    @Test
    void contextDoesNotStartWithTheOldExampleValue() {
        runner.withPropertyValues("jwt.secret=" + OLD_EXAMPLE_SECRET).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("JWT_SECRET")
                    .hasMessageNotContaining(OLD_EXAMPLE_SECRET);
            assertThat(fullMessages(context.getStartupFailure())).doesNotContain(OLD_EXAMPLE_SECRET);
        });
    }

    @Test
    void contextStartsWith32RandomBytes() {
        runner.withPropertyValues("jwt.secret=" + randomText(32)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtService.class);
        });
    }

    // ASCII only, so characters == bytes
    private static String randomText(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private static String fullMessages(Throwable failure) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
