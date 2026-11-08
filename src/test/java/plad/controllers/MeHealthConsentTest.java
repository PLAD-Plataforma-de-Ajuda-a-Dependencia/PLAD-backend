package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestConsents;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The consent to process health data (decision 35): a dependent's own step before the first triage, apart from the terms
// and the privacy policy of the sign-up. A "new version" is simulated with rows of an older one.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeHealthConsentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private PsychologistRepository psychologistRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aDependentIsPendingUntilItAcceptsTheVersionInForce() throws Exception {
        User user = newDependent();
        assertPending(user, true);

        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());

        assertPending(user, false);
        assertThat(rows(user)).containsExactly("health:" + TestConsents.HEALTH_VERSION);
    }

    @Test
    void aNewVersionMakesItPendingAgainAndTheOldRowStaysAsProof() throws Exception {
        User user = newDependent();
        insertConsent(user, "health", "0.9");
        assertPending(user, true);

        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());

        assertPending(user, false);
        assertThat(rows(user)).containsExactlyInAnyOrder("health:0.9", "health:" + TestConsents.HEALTH_VERSION);
    }

    @Test
    void acceptingTwiceDoesNotDuplicateRows() throws Exception {
        User user = newDependent();

        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());
        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());

        assertThat(rows(user)).hasSize(1);
    }

    @Test
    void aVersionThatIsNotInForceOrNoVersionIsRejectedAndStoresNothing() throws Exception {
        User user = newDependent();

        acceptHealth(user, "0.9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mockMvc.perform(post("/me/consents/health")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        assertThat(rows(user)).isEmpty();
        assertPending(user, true);
    }

    @Test
    void acceptingTheTermsAndThePrivacyPolicyDoesNotAcceptTheHealthConsent() throws Exception {
        User user = newDependent();

        mockMvc.perform(post("/me/consents")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted_terms_version\": \"1.0\", \"accepted_privacy_version\": \"1.0\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.consent_pending").value(false))
                .andExpect(jsonPath("$.health_consent_pending").value(true));
        // and the other way round: the health consent leaves the sign-up documents pending
        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());
        assertThat(rows(user)).containsExactlyInAnyOrder("terms:1.0", "privacy:1.0", "health:" + TestConsents.HEALTH_VERSION);
    }

    @Test
    void healthConsentAloneLeavesTheSignUpDocumentsPending() throws Exception {
        User user = newDependent();
        acceptHealth(user, TestConsents.HEALTH_VERSION).andExpect(status().isNoContent());

        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.consent_pending").value(true))
                .andExpect(jsonPath("$.health_consent_pending").value(false));
    }

    @Test
    void acceptingRecordsTheTimeAndTheClientIp() throws Exception {
        User user = newDependent();

        mockMvc.perform(post("/me/consents/health")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.7");
                            return request;
                        })
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TestConsents.HEALTH_VERSION)))
                .andExpect(status().isNoContent());

        var stored = jdbcTemplate.queryForList("SELECT ip, accepted_at FROM consents WHERE user_id = ?", user.getId());
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).get("ip")).isEqualTo("198.51.100.7");
        assertThat(stored.get(0).get("accepted_at")).isNotNull();
    }

    @Test
    void onlyADependentHasItAndOnlyADependentMayAcceptIt() throws Exception {
        User psychologist = newUser(UserType.psicologo);
        Psychologist row = new Psychologist();
        row.setUser(psychologist);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber("06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologistRepository.save(row);

        for (User other : new User[]{psychologist, newUser(UserType.admin)}) {
            // the key is always there and null: the flag is not theirs
            mockMvc.perform(get("/me").header("Authorization", bearer(other)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasKey("health_consent_pending")))
                    .andExpect(jsonPath("$.health_consent_pending").value(org.hamcrest.Matchers.nullValue()));

            acceptHealth(other, TestConsents.HEALTH_VERSION)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            assertThat(rows(other)).isEmpty();
        }
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/me/consents/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TestConsents.HEALTH_VERSION)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // helpers

    private void assertPending(User user, boolean expected) throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.health_consent_pending").value(expected));
    }

    private ResultActions acceptHealth(User user, String version) throws Exception {
        return mockMvc.perform(post("/me/consents/health")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(version)));
    }

    private static String body(String version) {
        return "{\"accepted_health_version\": \"%s\"}".formatted(version);
    }

    private List<String> rows(User user) {
        return jdbcTemplate.queryForList("SELECT document || ':' || version FROM consents WHERE user_id = ?",
                String.class, user.getId());
    }

    private void insertConsent(User user, String document, String version) {
        jdbcTemplate.update("INSERT INTO consents (user_id, document, version) VALUES (?, ?, ?)",
                user.getId(), document, version);
    }

    private User newDependent() {
        User user = newUser(UserType.dependente);
        Dependent dependent = new Dependent();
        dependent.setNickname(plad.TestNicknames.unique());
        dependent.setUser(user);
        dependentRepository.save(dependent);
        return user;
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName("Health Consent Test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
