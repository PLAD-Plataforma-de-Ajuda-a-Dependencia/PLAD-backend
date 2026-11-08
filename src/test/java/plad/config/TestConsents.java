package plad.config;

import org.springframework.jdbc.core.JdbcTemplate;

// POST/PUT /triage and the registration for a meeting refuse a dependent who has not accepted the health data consent
// (decision 35), and the registration also carries the participation term version (decision 36). Tests that are about
// something else give their dependents the consent with this, and ConsentVersionsTest fails if these constants drift
// from consent.health-version / consent.participation-version in application.properties.
public final class TestConsents {

    public static final String HEALTH_VERSION = "1.0";
    public static final String PARTICIPATION_VERSION = "1.0";

    private TestConsents() {
    }

    // plain SQL, in the caller's transaction: a @Transactional test rolls it back with the rest. The user must exist.
    public static void grantHealth(JdbcTemplate jdbc, Long userId) {
        jdbc.update("INSERT INTO consents (user_id, document, version) VALUES (?, 'health', ?) "
                + "ON CONFLICT DO NOTHING", userId, HEALTH_VERSION);
    }

    // the body of POST /meetings/{id}/registration with the participation term in force
    public static String participationBody() {
        return participationBody(PARTICIPATION_VERSION);
    }

    public static String participationBody(String version) {
        return "{\"accepted_participation_version\": \"%s\"}".formatted(version);
    }
}
