package plad.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    // static: Spring builds one context per test setup (properties, web environment, slices) and each of them
    // gets this same instance, so Postgres starts once per JVM instead of once per context
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return POSTGRES;
    }
}
