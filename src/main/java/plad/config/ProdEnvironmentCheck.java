package plad.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.List;

// application-prod.properties has no defaults for the database (${DB_URL}, ${DB_USER}, ${DB_PASSWORD}) nor for the live
// room (${LIVEKIT_URL}, ${LIVEKIT_API_KEY}, ${LIVEKIT_API_SECRET}, ${MEETING_IDENTITY_SECRET}), but Spring Boot
// leaves an unresolved placeholder as literal text when it binds spring.datasource.*, so a missing variable would only
// show up later as "'url' must start with jdbc". This stops the start with the names of the missing variables.
// Registered in META-INF/spring.factories.
public class ProdEnvironmentCheck implements EnvironmentPostProcessor, Ordered {

    static final List<String> REQUIRED = List.of("DB_URL", "DB_USER", "DB_PASSWORD",
            "LIVEKIT_URL", "LIVEKIT_API_KEY", "LIVEKIT_API_SECRET", "MEETING_IDENTITY_SECRET");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> missing = REQUIRED.stream()
                .filter(name -> {
                    String value = environment.getProperty(name);
                    return value == null || value.isBlank();
                })
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Profile prod requires these environment variables (no defaults): "
                    + String.join(", ", missing));
        }
    }

    // after ConfigDataEnvironmentPostProcessor, so spring.profiles.active from application.properties is already known
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
