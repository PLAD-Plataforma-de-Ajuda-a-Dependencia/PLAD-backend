package plad.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TRIAGE_QUESTIONS = "triageQuestions";

    // in-memory and never expiring: only the cached triage questions use it, and they change only through a Flyway
    // migration, which means a new deploy and a fresh JVM. Declared by hand because the cache auto-configuration
    // would need spring-boot-starter-cache.
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(TRIAGE_QUESTIONS);
    }
}
