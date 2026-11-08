package plad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

// The app refuses to start with a missing or weak LiveKit setting, like JWT_SECRET (see LiveKitProperties).
@Configuration
public class LiveKitConfig {

    @Bean
    public LiveKitProperties liveKitProperties(
            @Value("${livekit.url}") String url,
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret,
            @Value("${meeting.identity-secret}") String identitySecret,
            Environment environment) {
        return LiveKitProperties.validated(url, apiKey, apiSecret, identitySecret,
                environment.acceptsProfiles(Profiles.of("prod")));
    }
}
