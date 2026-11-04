package plad.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A "new version" is simulated with rows of an older version: the context keeps the versions in force from the properties
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeConsentTest {

    @Value("${consent.terms-version}")
    private String termsVersion;
    @Value("${consent.privacy-version}")
    private String privacyVersion;

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
    void userWithNoAcceptanceIsPendingUntilItAcceptsTheVersionsInForce() throws Exception {
        User user = newDependent();
        assertPending(user, true);

        accept(user, termsVersion, privacyVersion).andExpect(status().isNoContent());

        assertPending(user, false);
        assertThat(rows(user)).containsExactlyInAnyOrder("terms:" + termsVersion, "privacy:" + privacyVersion);
    }

    @Test
    void aNewVersionMakesTheConsentPendingAgainUntilItIsAccepted() throws Exception {
        User user = newDependent();
        // accepted an older version of both documents: not enough for the versions in force
        insertConsent(user, "terms", "0.9");
        insertConsent(user, "privacy", "0.9");
        assertPending(user, true);

        accept(user, termsVersion, privacyVersion).andExpect(status().isNoContent());

        assertPending(user, false);
        // the old acceptance stays as proof
        assertThat(rows(user)).containsExactlyInAnyOrder("terms:0.9", "privacy:0.9",
                "terms:" + termsVersion, "privacy:" + privacyVersion);
    }

    @Test
    void acceptingOnlyOneOfTheTwoDocumentsLeavesItPending() throws Exception {
        User user = newDependent();
        insertConsent(user, "terms", termsVersion);

        assertPending(user, true);
    }

    @Test
    void acceptingTwiceDoesNotDuplicateRows() throws Exception {
        User user = newDependent();

        accept(user, termsVersion, privacyVersion).andExpect(status().isNoContent());
        accept(user, termsVersion, privacyVersion).andExpect(status().isNoContent());

        assertThat(rows(user)).hasSize(2);
    }

    @Test
    void aVersionThatIsNotInForceIsRejectedAndStoresNothing() throws Exception {
        User user = newDependent();

        accept(user, "0.9", privacyVersion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        accept(user, termsVersion, "0.9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        assertThat(rows(user)).isEmpty();
        assertPending(user, true);
    }

    @Test
    void aMissingVersionIsRejected() throws Exception {
        User user = newDependent();

        mockMvc.perform(post("/me/consents")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        assertThat(rows(user)).isEmpty();
    }

    @Test
    void acceptingRecordsTheTimeAndTheClientIp() throws Exception {
        User user = newDependent();

        mockMvc.perform(post("/me/consents")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.7");
                            return request;
                        })
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(termsVersion, privacyVersion)))
                .andExpect(status().isNoContent());

        var stored = jdbcTemplate.queryForList("SELECT ip, accepted_at FROM consents WHERE user_id = ?", user.getId());
        assertThat(stored).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("ip")).isEqualTo("198.51.100.7");
            assertThat(row.get("accepted_at")).isNotNull();
        });
    }

    @Test
    void everyAccountTypeSeesConsentPending() throws Exception {
        User psychologistUser = newUser(UserType.psicologo);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(psychologistUser);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber("06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologistRepository.save(psychologist);

        for (User user : new User[]{newDependent(), psychologistUser, newUser(UserType.admin)}) {
            mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasKey("consent_pending")))
                    .andExpect(jsonPath("$.consent_pending").value(true));
        }
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/me/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(termsVersion, privacyVersion)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // helpers

    private void assertPending(User user, boolean expected) throws Exception {
        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consent_pending").value(expected));
    }

    private ResultActions accept(User user, String terms, String privacy) throws Exception {
        return mockMvc.perform(post("/me/consents")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(terms, privacy)));
    }

    private static String body(String terms, String privacy) {
        return "{\"accepted_terms_version\": \"%s\", \"accepted_privacy_version\": \"%s\"}".formatted(terms, privacy);
    }

    private java.util.List<String> rows(User user) {
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
        user.setRealName("Consent Test");
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
