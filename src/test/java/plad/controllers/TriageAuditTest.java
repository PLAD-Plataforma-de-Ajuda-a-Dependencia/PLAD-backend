package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.entity.Dependent;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;
import plad.repositories.DependentRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// writing the triage leaves a trace in audit_log, in the same transaction, without any of its content (RNF003)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class TriageAuditTest {

    private static final String SECRET_FREE_TEXT = "segredo-unico-do-dependente-4711";
    private static final String REAL_NAME = "Maria Auditada Silva";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private QuestionTriageRepository questionTriageRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;
    private String singleChoiceCode;
    private String singleChoiceOption;
    private String freeTextCode;

    @BeforeEach
    void createDependent() {
        user = new User();
        user.setRealName(REAL_NAME);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
        user = userRepository.save(user);
        Dependent dependent = new Dependent();
        dependent.setNickname(plad.TestNicknames.unique());
        dependent.setUser(user);
        dependentRepository.save(dependent);
        token = jwtService.generateToken(user);

        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
        QuestionTriage single = questions.stream().filter(q -> q.getQuestionType() == QuestionType.single_choice).findFirst().orElseThrow();
        singleChoiceCode = single.getCode();
        singleChoiceOption = single.getOptions().get(0).get("code");
        freeTextCode = questions.stream().filter(q -> q.getQuestionType() == QuestionType.free_text).findFirst().orElseThrow().getCode();
    }

    @Test
    void submitRecordsTriageSubmittedWithIdsAndRoleOnly() throws Exception {
        String response = submit(fullBody()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long triageId = ((Number) JsonPath.read(response, "$.id")).longValue();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(entry -> user.getId().equals(entry.getSubjectUserId())).toList();
        assertThat(entries).hasSize(1);
        AuditLog entry = entries.get(0);
        assertThat(entry.getAction()).isEqualTo(AuditAction.TRIAGE_SUBMITTED);
        assertThat(entry.getActorId()).isEqualTo(user.getId());
        assertThat(entry.getEntity()).isEqualTo("TRIAGE");
        assertThat(entry.getEntityId()).isEqualTo(triageId);
        assertThat(entry.getDetails()).isEqualTo(Map.of("actor_role", "dependente"));
    }

    @Test
    void resubmitRecordsTriageResubmitted() throws Exception {
        submit(fullBody()).andExpect(status().isCreated());
        resubmit(fullBody()).andExpect(status().isOk());

        assertThat(actionsOfUser()).containsExactly(AuditAction.TRIAGE_SUBMITTED, AuditAction.TRIAGE_RESUBMITTED);
    }

    @Test
    void nothingIsRecordedWhenNothingIsStored() throws Exception {
        submit("{\"answers\": []}").andExpect(status().isNoContent());
        resubmit(fullBody()).andExpect(status().isNotFound());
        assertThat(actionsOfUser()).isEmpty();

        submit(fullBody()).andExpect(status().isCreated());
        submit(fullBody()).andExpect(status().isConflict());
        resubmit("{\"answers\": []}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRIAGE"));
        assertThat(actionsOfUser()).containsExactly(AuditAction.TRIAGE_SUBMITTED);
    }

    // the log must not become another copy of the sensitive data
    @Test
    void detailsNeverHoldAnswersNameOrEmail() throws Exception {
        submit(fullBody()).andExpect(status().isCreated());
        resubmit(fullBody()).andExpect(status().isOk());

        auditLogRepository.flush();
        List<String> rows = jdbcTemplate.queryForList(
                "select coalesce(details::text, '') || ' ' || coalesce(entity, '') from audit_log where subject_user_id = ?",
                String.class, user.getId());
        assertThat(rows).hasSize(2);
        for (String row : rows) {
            assertThat(row).doesNotContain(SECRET_FREE_TEXT)
                    .doesNotContain(singleChoiceOption)
                    .doesNotContain(REAL_NAME)
                    .doesNotContain(user.getEmail())
                    .doesNotContain("answers");
        }
    }

    private List<AuditAction> actionsOfUser() {
        return auditLogRepository.findAll().stream()
                .filter(entry -> user.getId().equals(entry.getSubjectUserId()))
                .sorted(java.util.Comparator.comparing(AuditLog::getId))
                .map(AuditLog::getAction)
                .toList();
    }

    private String fullBody() {
        return "{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}, {\"question_code\": \"%s\", \"values\": [\"%s\"]}]}"
                .formatted(singleChoiceCode, singleChoiceOption, freeTextCode, SECRET_FREE_TEXT);
    }

    private ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post("/triage")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions resubmit(String body) throws Exception {
        return mockMvc.perform(put("/triage")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
