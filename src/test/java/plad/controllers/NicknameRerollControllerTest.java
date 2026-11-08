package plad.controllers;

import com.jayway.jsonpath.JsonPath;
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
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.entity.Dependent;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;
import plad.repositories.DependentRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /me/nickname/reroll (dependents only, 5 a day) and the rule that a dependent never sees another
// dependent's real name
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class NicknameRerollControllerTest {

    private static final String OTHER_REAL_NAME = "Fulano Secreto Exposto";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rerollReturnsANewNicknameThatGetMeThenShows() throws Exception {
        User user = newDependent("Dependente Teste");
        String before = nicknameOf(user);

        String body = reroll(user).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String after = JsonPath.read(body, "$.nickname");

        assertThat(after).isNotBlank().isNotEqualTo(before);
        mockMvc.perform(get("/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(after));
        assertThat(nicknameOf(user)).isEqualTo(after);
    }

    @Test
    void rerollKeepsTheOldNicknameInTheHistoryAndAudits() throws Exception {
        User user = newDependent("Dependente Teste");
        String before = nicknameOf(user);
        String after = JsonPath.read(reroll(user).andReturn().getResponse().getContentAsString(), "$.nickname");

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT nickname, replaced_at FROM dependent_nickname_history WHERE dependent_id = ? ORDER BY id", user.getId());
        assertThat(history).extracting(row -> row.get("nickname")).containsExactly(before, after);
        assertThat(history.get(0).get("replaced_at")).isNotNull();
        assertThat(history.get(1).get("replaced_at")).isNull();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.NICKNAME_REROLLED && user.getId().equals(e.getSubjectUserId()))
                .toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getDetails()).isEqualTo(Map.of("actor_role", "dependente"));
        // a reroll is not a third-party read, so the data subject's access log stays empty
        mockMvc.perform(get("/me/access-log").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void sixthRerollInADayIsRateLimitedWithRetryAfter() throws Exception {
        User user = newDependent("Dependente Teste");

        for (int i = 0; i < 5; i++) {
            reroll(user).andExpect(status().isOk());
        }

        reroll(user).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // the limit is per dependent: someone else can still reroll
        reroll(newDependent("Outro Dependente")).andExpect(status().isOk());
    }

    @Test
    void psychologistAndAdminCannotReroll() throws Exception {
        for (UserType type : List.of(UserType.psicologo, UserType.admin)) {
            reroll(newUser(type, "Nao Dependente")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/me/nickname/reroll")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // RF007: what a dependent gets back never carries another dependent's real name, e-mail or nickname
    @Test
    void aDependentNeverReceivesAnotherDependentsRealName() throws Exception {
        User other = newDependent(OTHER_REAL_NAME);
        mockMvc.perform(post("/triage")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\": [{\"question_code\": \"AGE_RANGE\", \"values\": [\"AGE_25_39\"]}]}"))
                .andExpect(status().isCreated());
        User me = newDependent("Dependente Comum");

        List<String> bodies = List.of(
                mockMvc.perform(get("/me").header("Authorization", bearer(me))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/me/access-log").header("Authorization", bearer(me))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/triage/questions").header("Authorization", bearer(me))).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(get("/triages").header("Authorization", bearer(me))).andReturn().getResponse().getContentAsString(),
                reroll(me).andReturn().getResponse().getContentAsString());

        for (String body : bodies) {
            assertThat(body).doesNotContain(OTHER_REAL_NAME).doesNotContain(other.getEmail());
        }
    }

    // helpers

    private ResultActions reroll(User user) throws Exception {
        return mockMvc.perform(post("/me/nickname/reroll").header("Authorization", bearer(user)));
    }

    private String nicknameOf(User user) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM dependents WHERE user_id = ?", String.class, user.getId());
    }

    // goes through the real flow of registration: dependents row, nickname and history row
    private User newDependent(String realName) {
        User user = newUser(UserType.dependente, realName);
        String nickname = "Teste " + UUID.randomUUID().toString().substring(0, 8);
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependent.setNickname(nickname);
        dependentRepository.saveAndFlush(dependent);
        jdbcTemplate.update("INSERT INTO dependent_nickname_history (dependent_id, nickname) VALUES (?, ?)", user.getId(), nickname);
        TestConsents.grantHealth(jdbcTemplate, user.getId());
        return user;
    }

    private User newUser(UserType type, String realName) {
        User user = new User();
        user.setRealName(realName);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
