package plad.controllers;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.AuditService;
import plad.services.ConsentService;
import plad.services.JwtService;
import plad.services.RefreshTokenService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DELETE /me erases the account and everything tied to it (LGPD art. 18, VI), except audit_log, which keeps ids only.
// @Transactional: ON DELETE CASCADE runs inside the test transaction, so the counts below see it, and the audit
// entries the test writes (the table accepts no deletes) roll back with it.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AccountDeletionTest {

    private static final String PASSWORD = "senha-dev-123";
    private static final String ORIGIN = "http://localhost:5173"; // the default cors.allowed-origins
    // login and refresh are limited per client IP, and the limiter is shared by the cached context
    private static final AtomicInteger CLIENTS = new AtomicInteger();

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
    private QuestionTriageRepository questionTriageRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ConsentService consentService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void deletingADependentLeavesNoRowWithTheirIdExceptInTheAuditLog() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        String token = jwtService.generateToken(user);
        submitTriage(token);
        refreshTokenService.start(user);
        auditService.record(AuditAction.TRIAGE_VIEWED, 999_001L, UserType.psicologo, user.getId(), "TRIAGE", 1L, null);
        assertThat(nonEmpty(user)).as("the fixture has a row in every table of a dependent").containsExactlyInAnyOrder(
                "users", "dependents", "triages", "result_questions_triages", "consents",
                "dependent_nickname_history", "refresh_tokens");

        delete(token, PASSWORD).andExpect(status().isNoContent());

        assertThat(nonEmpty(user)).isEmpty();
        // the record that someone read the triage stays: ids only, no FK to users
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE subject_user_id = ?", Long.class, user.getId()))
                .isEqualTo(2L); // TRIAGE_SUBMITTED + TRIAGE_VIEWED
    }

    @Test
    void deletingAPsychologistRemovesTheirRows() throws Exception {
        User user = newUser(UserType.psicologo);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber("06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        psychologistRepository.save(psychologist);
        consentService.record(user, "198.51.100.7");
        refreshTokenService.start(user);
        // a meeting they host goes with them (meetings.psychologist_id cascades, V25)
        jdbc.update("INSERT INTO meetings (psychologist_id, title, starts_at, ends_at, capacity) "
                + "VALUES (?, 'Grupo', now() + interval '1 day', now() + interval '1 day 1 hour', 5)", user.getId());
        assertThat(nonEmpty(user)).containsExactlyInAnyOrder("users", "psychologists", "consents", "refresh_tokens", "meetings");

        delete(jwtService.generateToken(user), PASSWORD).andExpect(status().isNoContent());

        assertThat(nonEmpty(user)).isEmpty();
    }

    @Test
    void afterTheDeleteTheOldCredentialsAreDead() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        String token = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.start(user).refreshToken();

        delete(token, PASSWORD).andExpect(status().isNoContent());

        // login fails with the same message as a wrong password
        mockMvc.perform(post("/auth/login").with(newClient()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        // the refresh token is gone
        mockMvc.perform(post("/auth/refresh").with(newClient()).header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        // and so is the access token that was still inside its 15 minutes
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theEmailIsFreeAgainAfterTheDelete() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        String email = user.getEmail();

        delete(jwtService.generateToken(user), PASSWORD).andExpect(status().isNoContent());

        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    void theResponseClearsTheRefreshCookie() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);

        delete(jwtService.generateToken(user), PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void aWrongPasswordDeletesNothing() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        Map<String, Long> before = totalRowsOf(user);

        delete(jwtService.generateToken(user), "outra-senha-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(totalRowsOf(user)).isEqualTo(before);
    }

    @Test
    void aMissingOrBlankPasswordIsABadRequestAndDeletesNothing() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        String token = jwtService.generateToken(user);

        for (String body : List.of("{}", "{\"password\": \"\"}", "{\"password\": \"   \"}")) {
            mockMvc.perform(MockMvcRequestBuilders.delete("/me")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(MockMvcRequestBuilders.delete("/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsById(user.getId())).isTrue();
    }

    @Test
    void wrongPasswordsShareTheLoginBudgetOfTheAccount() throws Exception {
        User user = newUser(UserType.dependente);
        newDependent(user);
        String token = jwtService.generateToken(user);

        // login-email budget: 10 failures per 15 minutes; the 11th is 429 even with the right password
        for (int i = 0; i < 10; i++) {
            delete(token, "errada-" + i).andExpect(status().isUnauthorized());
        }
        delete(token, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        assertThat(userRepository.existsById(user.getId())).isTrue();
    }

    @Test
    void anAdminCannotDeleteTheirAccountThroughTheApi() throws Exception {
        User admin = newUser(UserType.admin);

        delete(jwtService.generateToken(admin), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/me")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void deletingOneAccountLeavesAnotherUntouched() throws Exception {
        User deleted = newUser(UserType.dependente);
        newDependent(deleted);
        User other = newUser(UserType.dependente);
        newDependent(other);
        Map<String, Long> otherBefore = totalRowsOf(other);

        delete(jwtService.generateToken(deleted), PASSWORD).andExpect(status().isNoContent());

        assertThat(totalRowsOf(other)).isEqualTo(otherBefore);
    }

    // The cascade is what erases everything: a table added later with a foreign key to users or dependents that does
    // not cascade would block DELETE /me with a 409. This fails first, naming the constraint.
    @Test
    void everyForeignKeyToUsersOrDependentsCascades() {
        List<Map<String, Object>> notCascading = jdbc.queryForList("""
                SELECT c.conrelid::regclass::text AS tbl, c.conname AS name
                FROM pg_constraint c
                WHERE c.contype = 'f'
                  AND c.confrelid IN ('users'::regclass, 'dependents'::regclass)
                  AND c.confdeltype <> 'c'""");
        assertThat(notCascading).as("foreign keys to users/dependents without ON DELETE CASCADE").isEmpty();
    }

    // --- helpers

    private ResultActions delete(String token, String password) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"%s\"}".formatted(password)));
    }

    private static RequestPostProcessor newClient() {
        String address = "198.51.100." + (CLIENTS.incrementAndGet() % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName("Fulana de Tal " + type);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setBirthDate(LocalDate.of(1995, 3, 4));
        user.setGender(Sex.feminino);
        user.setTypeUser(type);
        return userRepository.save(user);
    }

    // dependents row, nickname history, consents and a refresh token: one row in every table the cascade must reach
    private Dependent newDependent(User user) {
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependent.setNickname(plad.TestNicknames.unique());
        dependent = dependentRepository.save(dependent);
        dependentRepository.openNicknameHistory(user.getId(), dependent.getNickname());
        consentService.record(user, "198.51.100.7");
        return dependent;
    }

    private void submitTriage(String token) throws Exception {
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
        QuestionTriage single = questions.stream().filter(q -> q.getQuestionType() == QuestionType.single_choice).findFirst().orElseThrow();
        QuestionTriage free = questions.stream().filter(q -> q.getQuestionType() == QuestionType.free_text).findFirst().orElseThrow();
        mockMvc.perform(post("/triage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}, {\"question_code\": \"%s\", \"values\": [\"texto livre\"]}]}"
                                .formatted(single.getCode(), single.getOptions().get(0).get("code"), free.getCode())))
                .andExpect(status().isCreated());
    }

    // names of the tables that still hold a row of this user
    private List<String> nonEmpty(User user) {
        return totalRowsOf(user).entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).toList();
    }

    // rows that reference the user, per table
    private Map<String, Long> totalRowsOf(User user) {
        Long id = user.getId();
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("users", count("SELECT count(*) FROM users WHERE id = ?", id));
        counts.put("dependents", count("SELECT count(*) FROM dependents WHERE user_id = ?", id));
        counts.put("psychologists", count("SELECT count(*) FROM psychologists WHERE user_id = ?", id));
        counts.put("triages", count("SELECT count(*) FROM triages WHERE dependent_id = ?", id));
        counts.put("result_questions_triages", count(
                "SELECT count(*) FROM result_questions_triages r JOIN triages t ON t.id = r.triage_id WHERE t.dependent_id = ?", id));
        counts.put("consents", count("SELECT count(*) FROM consents WHERE user_id = ?", id));
        counts.put("dependent_nickname_history", count("SELECT count(*) FROM dependent_nickname_history WHERE dependent_id = ?", id));
        counts.put("refresh_tokens", count("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", id));
        counts.put("meetings", count("SELECT count(*) FROM meetings WHERE psychologist_id = ?", id));
        return counts;
    }

    private long count(String sql, Long id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }
}
