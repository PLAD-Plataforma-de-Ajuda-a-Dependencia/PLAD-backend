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
import plad.entity.CrmCrpType;
import plad.entity.Dependent;
import plad.entity.Psychologist;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.AuditLogRepository;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /triages and GET /triages/{id}: any admin or psychologist reads, as often as they want; a dependent never
// does (not even their own triage); every triage returned leaves a TRIAGE_VIEWED entry (RNF003)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class TriageReadControllerTest {

    private static final String SECRET_FREE_TEXT = "segredo-unico-da-leitura-9183";
    private static final String REAL_NAME = "Joana Lida Pelo Psicologo";

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
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User psychologist;
    private String choiceCode;
    private String choiceOption;
    private String freeTextCode;

    @BeforeEach
    void setUp() {
        admin = newUser(UserType.admin, "Admin Leitor");
        // not validated on purpose: validation plays no part in who reads
        psychologist = newUser(UserType.psicologo, "Psicologo Leitor");
        Psychologist row = new Psychologist();
        row.setUser(psychologist);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.save(row);

        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
        QuestionTriage choice = questions.stream().filter(q -> q.getQuestionType() == QuestionType.single_choice).findFirst().orElseThrow();
        choiceCode = choice.getCode();
        choiceOption = choice.getOptions().get(0).get("code");
        freeTextCode = questions.stream().filter(q -> q.getQuestionType() == QuestionType.free_text).findFirst().orElseThrow().getCode();
    }

    @Test
    void adminListsTriagesNewestFirstAndReadsTheDetail() throws Exception {
        User first = newDependent(REAL_NAME);
        User second = newDependent("Outro Dependente");
        long firstTriage = submit(first);
        long secondTriage = submit(second);

        String response = read("/triages?size=100", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(response, "$.items[*].id");
        assertThat(ids.indexOf((int) secondTriage)).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf((int) firstTriage));

        read("/triages/" + firstTriage, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstTriage))
                .andExpect(jsonPath("$.dependent_id").value(first.getId()))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.answers.length()").value(2))
                .andExpect(jsonPath("$.answers[?(@.question_code == '%s')].values[0]".formatted(choiceCode)).value(choiceOption))
                .andExpect(jsonPath("$.answers[?(@.question_code == '%s')].values[0]".formatted(freeTextCode)).value(SECRET_FREE_TEXT));
    }

    @Test
    void psychologistReadsListAndDetailWithoutBeingValidated() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].dependent_id".formatted(triageId)).value(dependent.getId().intValue()));
        read("/triages/" + triageId, psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(triageId));
    }

    @Test
    void dependentCannotReadAnyTriageNotEvenTheirOwn() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/triages", dependent).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        read("/triages/" + triageId, dependent).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(viewsOf(dependent)).isEmpty();
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/triages")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/triages/1")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownTriageIsNotFoundAndWritesNothing() throws Exception {
        long before = auditLogRepository.count();

        read("/triages/999999999", admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"));

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        read("/triages?size=101", admin).andExpect(status().isBadRequest());
        read("/triages?size=0", admin).andExpect(status().isBadRequest());
        read("/triages?page=-1", admin).andExpect(status().isBadRequest());
    }

    @Test
    void responseIdentifiesTheDependentOnlyById() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        String detail = read("/triages/" + triageId, admin).andReturn().getResponse().getContentAsString();
        String list = read("/triages?size=100", admin).andReturn().getResponse().getContentAsString();

        for (String body : List.of(detail, list)) {
            assertThat(body).doesNotContain(REAL_NAME).doesNotContain(dependent.getEmail());
        }
    }

    @Test
    void detailReadRecordsOneViewWithActorSubjectAndRole() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/triages/" + triageId, psychologist).andExpect(status().isOk());

        List<AuditLog> views = viewsOf(dependent);
        assertThat(views).hasSize(1);
        AuditLog view = views.get(0);
        assertThat(view.getActorId()).isEqualTo(psychologist.getId());
        assertThat(view.getSubjectUserId()).isEqualTo(dependent.getId());
        assertThat(view.getEntity()).isEqualTo("TRIAGE");
        assertThat(view.getEntityId()).isEqualTo(triageId);
        assertThat(view.getDetails()).isEqualTo(Map.of("actor_role", "psicologo"));
    }

    @Test
    void adminReadIsRecordedWithTheAdminRole() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/triages/" + triageId, admin).andExpect(status().isOk());

        List<AuditLog> views = viewsOf(dependent);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getActorId()).isEqualTo(admin.getId());
        assertThat(views.get(0).getDetails()).isEqualTo(Map.of("actor_role", "admin"));
    }

    @Test
    void readingTheSameTriageTwiceRecordsTwoViews() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/triages/" + triageId, psychologist).andExpect(status().isOk());
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());
        read("/triages/" + triageId, admin).andExpect(status().isOk());

        assertThat(viewsOf(dependent)).hasSize(3);
    }

    @Test
    void listRecordsOneViewPerTriageReturned() throws Exception {
        User first = newDependent("Primeiro");
        User second = newDependent("Segundo");
        User third = newDependent("Terceiro");
        submit(first);
        long secondTriage = submit(second);
        long thirdTriage = submit(third);
        long before = auditLogRepository.findAll().stream().filter(e -> e.getAction() == AuditAction.TRIAGE_VIEWED).count();

        // size 2 returns the two newest only
        read("/triages?size=2", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(thirdTriage))
                .andExpect(jsonPath("$.items[1].id").value(secondTriage));

        long after = auditLogRepository.findAll().stream().filter(e -> e.getAction() == AuditAction.TRIAGE_VIEWED).count();
        assertThat(after - before).isEqualTo(2);
        assertThat(viewsOf(third)).hasSize(1);
        assertThat(viewsOf(second)).hasSize(1);
        assertThat(viewsOf(first)).isEmpty();
        assertThat(viewsOf(third).get(0).getEntityId()).isEqualTo(thirdTriage);
    }

    @Test
    void viewEntriesNeverHoldAnswersNameOrEmail() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        read("/triages/" + triageId, admin).andExpect(status().isOk());
        read("/triages?size=100", admin).andExpect(status().isOk());

        auditLogRepository.flush();
        List<String> rows = jdbcTemplate.queryForList(
                "select coalesce(details::text, '') || ' ' || coalesce(entity, '') from audit_log where subject_user_id = ? and action = 'TRIAGE_VIEWED'",
                String.class, dependent.getId());
        assertThat(rows).hasSize(2);
        for (String row : rows) {
            assertThat(row).doesNotContain(SECRET_FREE_TEXT)
                    .doesNotContain(choiceOption)
                    .doesNotContain(REAL_NAME)
                    .doesNotContain(dependent.getEmail())
                    .doesNotContain("answers");
        }
    }

    @Test
    void dependentSeesTheReadInTheirAccessLogWithTheRoleButNotTheReader() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());

        String body = read("/me/access-log", dependent).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_VIEWED"))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("actor_id").doesNotContain(String.valueOf(psychologist.getId()) + ",");
    }

    private ResultActions read(String path, User reader) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(reader)));
    }

    // POST /triage as the dependent; returns the new triage id
    private long submit(User dependent) throws Exception {
        String body = "{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}, {\"question_code\": \"%s\", \"values\": [\"%s\"]}]}"
                .formatted(choiceCode, choiceOption, freeTextCode, SECRET_FREE_TEXT);
        String response = mockMvc.perform(post("/triage")
                        .header("Authorization", "Bearer " + jwtService.generateToken(dependent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private List<AuditLog> viewsOf(User dependent) {
        return auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.TRIAGE_VIEWED)
                .filter(entry -> dependent.getId().equals(entry.getSubjectUserId()))
                .sorted(Comparator.comparing(AuditLog::getId))
                .toList();
    }

    private User newDependent(String realName) {
        User user = newUser(UserType.dependente, realName);
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependentRepository.save(dependent);
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
        return userRepository.save(user);
    }
}
