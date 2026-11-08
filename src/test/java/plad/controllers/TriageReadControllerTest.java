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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /triages (psychologist, with the real name) and GET /admin/triages (admin, nickname only). The admin reads every
// triage; a psychologist only reads the triage of a dependent registered in a meeting of theirs that was not cancelled
// (decision 13, LGPD art. 6, III), as often as they want; a dependent never does (not even their own triage); every
// triage returned leaves a TRIAGE_VIEWED entry (RNF003) and a refused one leaves none
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

    private LiveRoomFixtures fixtures;
    private User admin;
    private User psychologist;
    private String choiceCode;
    private String choiceOption;
    private String freeTextCode;
    private int slot; // spaces the scheduled meetings of one host two hours apart, so they never overlap

    @BeforeEach
    void setUp() {
        fixtures = new LiveRoomFixtures(userRepository, dependentRepository, psychologistRepository, jdbcTemplate, jwtService);
        admin = newUser(UserType.admin, "Admin Leitor");
        // not validated on purpose: validation plays no part in who reads
        psychologist = newPsychologist("Psicologo Leitor");

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

        String response = read("/admin/triages?size=100", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(response, "$.items[*].id");
        assertThat(ids.indexOf((int) secondTriage)).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf((int) firstTriage));

        read("/admin/triages/" + firstTriage, admin).andExpect(status().isOk())
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
        enroll(dependent);

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

        read("/admin/triages/999999999", admin).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"));

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidPagingIsBadRequest() throws Exception {
        read("/admin/triages?size=101", admin).andExpect(status().isBadRequest());
        read("/admin/triages?size=0", admin).andExpect(status().isBadRequest());
        read("/admin/triages?page=-1", admin).andExpect(status().isBadRequest());
        read("/triages?size=101", psychologist).andExpect(status().isBadRequest());
        read("/triages?size=0", psychologist).andExpect(status().isBadRequest());
        read("/triages?page=-1", psychologist).andExpect(status().isBadRequest());
    }

    // Decision 15: only a psychologist (validated or not) gets the dependent's real name, next to the nickname
    @Test
    void psychologistWhoIsNotValidatedGetsNicknameAndRealNameButNeverTheEmail() throws Exception {
        User dependent = newDependent(REAL_NAME);
        String nickname = dependentRepository.findById(dependent.getId()).orElseThrow().getNickname();
        long triageId = submit(dependent);
        enroll(dependent);

        String detail = read("/triages/" + triageId, psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.dependent.nickname").value(nickname))
                .andExpect(jsonPath("$.dependent.real_name").value(REAL_NAME))
                .andReturn().getResponse().getContentAsString();
        String list = read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].dependent.nickname".formatted(triageId)).value(nickname))
                .andExpect(jsonPath("$.items[?(@.id == %d)].dependent.real_name".formatted(triageId)).value(REAL_NAME))
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(detail, list)) {
            assertThat(body).doesNotContain(dependent.getEmail());
        }
    }

    @Test
    void adminGetsOnlyTheNicknameNeverTheRealName() throws Exception {
        User dependent = newDependent(REAL_NAME);
        String nickname = dependentRepository.findById(dependent.getId()).orElseThrow().getNickname();
        long triageId = submit(dependent);

        String detail = read("/admin/triages/" + triageId, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.dependent_id").value(dependent.getId()))
                .andExpect(jsonPath("$.dependent.nickname").value(nickname))
                .andExpect(jsonPath("$.dependent", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("real_name"))))
                .andReturn().getResponse().getContentAsString();
        String list = read("/admin/triages?size=100", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].dependent.nickname".formatted(triageId)).value(nickname))
                .andReturn().getResponse().getContentAsString();

        for (String body : List.of(detail, list)) {
            assertThat(body).doesNotContain(REAL_NAME).doesNotContain("real_name").doesNotContain(dependent.getEmail());
        }
    }

    @Test
    void eachRoleOnlyReadsOnItsOwnRoute() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        // the admin is not a psychologist: no real name through /triages
        read("/triages?size=100", admin).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        read("/triages/" + triageId, admin).andExpect(status().isForbidden());
        // and the psychologist has no business on /admin
        read("/admin/triages?size=100", psychologist).andExpect(status().isForbidden());
        read("/admin/triages/" + triageId, psychologist).andExpect(status().isForbidden());
        // a dependent reaches neither
        read("/admin/triages?size=100", dependent).andExpect(status().isForbidden());
        read("/admin/triages/" + triageId, dependent).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/triages")).andExpect(status().isUnauthorized());
        assertThat(viewsOf(dependent)).isEmpty();
    }

    @Test
    void aDependentNeverGetsAnotherDependentsRealNameFromTheTriageRoutes() throws Exception {
        User owner = newDependent(REAL_NAME);
        long triageId = submit(owner);
        User other = newDependent("Outro Dependente Curioso");

        for (String path : List.of("/triages?size=100", "/triages/" + triageId,
                "/admin/triages?size=100", "/admin/triages/" + triageId)) {
            String body = read(path, other).andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(REAL_NAME);
        }
    }

    @Test
    void detailReadRecordsOneViewWithActorSubjectAndRole() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        enroll(dependent);

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

        read("/admin/triages/" + triageId, admin).andExpect(status().isOk());

        List<AuditLog> views = viewsOf(dependent);
        assertThat(views).hasSize(1);
        assertThat(views.get(0).getActorId()).isEqualTo(admin.getId());
        assertThat(views.get(0).getDetails()).isEqualTo(Map.of("actor_role", "admin"));
    }

    @Test
    void readingTheSameTriageTwiceRecordsTwoViews() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        enroll(dependent);

        read("/triages/" + triageId, psychologist).andExpect(status().isOk());
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());
        read("/admin/triages/" + triageId, admin).andExpect(status().isOk());

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
        enroll(first);
        enroll(second);
        enroll(third);
        long before = triageViews();

        // size 2 returns the two newest only
        read("/triages?size=2", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(thirdTriage))
                .andExpect(jsonPath("$.items[1].id").value(secondTriage));

        long after = triageViews();
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
        read("/admin/triages/" + triageId, admin).andExpect(status().isOk());
        read("/admin/triages?size=100", admin).andExpect(status().isOk());

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
        enroll(dependent);
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());

        String body = read("/me/access-log", dependent).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("TRIAGE_VIEWED"))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("actor_id").doesNotContain(String.valueOf(psychologist.getId()) + ",");
    }

    // ---- which triages a psychologist may read (decision 13) ---------------------------------------------------------

    @Test
    void aPsychologistWithNoMeetingSeesAnEmptyListAndNothingIsRecorded() throws Exception {
        User dependent = newDependent(REAL_NAME);
        submit(dependent);
        long before = triageViews();

        String body = read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total_elements").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(REAL_NAME);
        assertThat(triageViews()).isEqualTo(before);
    }

    @Test
    void aPsychologistSeesOnlyTheDependentsRegisteredInTheirOwnMeetings() throws Exception {
        User other = newPsychologist("Outro Psicologo");
        User mine = newDependent("Meu Inscrito");
        User theirs = newDependent("Inscrito Do Outro");
        User nobodys = newDependent("Sem Reuniao Nenhuma");
        long mineTriage = submit(mine);
        long theirsTriage = submit(theirs);
        submit(nobodys);
        enroll(psychologist, mine, "scheduled");
        enroll(other, theirs, "scheduled");

        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mineTriage))
                .andExpect(jsonPath("$.items[0].dependent_id").value(mine.getId()));
        read("/triages?size=100", other).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(theirsTriage));

        assertThat(viewsOf(mine)).hasSize(1);
        assertThat(viewsOf(theirs)).hasSize(1); // read by the other psychologist, not by this one
        assertThat(viewsOf(theirs).get(0).getActorId()).isEqualTo(other.getId());
        assertThat(viewsOf(nobodys)).isEmpty();
    }

    // 404 and not 403, so the answer does not reveal that the triage exists; and nothing is written to the audit trail
    @Test
    void aTriageOutsideTheScopeIsNotFoundLikeAnUnknownIdAndWritesNothing() throws Exception {
        User other = newPsychologist("Outro Psicologo");
        User theirs = newDependent(REAL_NAME);
        User nobodys = newDependent("Sem Reuniao Nenhuma");
        long theirsTriage = submit(theirs);
        long nobodysTriage = submit(nobodys);
        enroll(other, theirs, "scheduled");
        long before = triageViews();

        String ofAnother = read("/triages/" + theirsTriage, psychologist).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String ofNobody = read("/triages/" + nobodysTriage, psychologist).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String unknown = read("/triages/999999999", psychologist).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(ofAnother, "$.message")).isEqualTo(JsonPath.<String>read(unknown, "$.message"));
        assertThat(JsonPath.<String>read(ofNobody, "$.message")).isEqualTo(JsonPath.<String>read(unknown, "$.message"));
        assertThat(ofAnother).doesNotContain(REAL_NAME);
        assertThat(triageViews()).isEqualTo(before);
        assertThat(viewsOf(theirs)).isEmpty();
        assertThat(viewsOf(nobodys)).isEmpty();
    }

    @Test
    void cancellingTheMeetingTakesTheAccessAway() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        long meetingId = enroll(psychologist, dependent, "scheduled");
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());

        mockMvc.perform(post("/meetings/" + meetingId + "/cancel").header("Authorization", fixtures.bearer(psychologist)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
        long before = triageViews();

        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total_elements").value(0));
        read("/triages/" + triageId, psychologist).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"));
        assertThat(triageViews()).isEqualTo(before);
    }

    @Test
    void aDependentWhoseOnlyMeetingWasCancelledIsNotReadable() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        enroll(psychologist, dependent, "cancelled");

        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        read("/triages/" + triageId, psychologist).andExpect(status().isNotFound());
        assertThat(viewsOf(dependent)).isEmpty();
    }

    // "not cancelled" includes a meeting that already happened: scheduled and ended both give access
    @Test
    void anEndedMeetingKeepsTheAccess() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        long meetingId = fixtures.meeting(psychologist, start, start.plus(1, ChronoUnit.HOURS), "ended");
        fixtures.register(meetingId, dependent);

        read("/triages/" + triageId, psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.dependent_id").value(dependent.getId()));
        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void takingTheRegistrationBackTakesTheAccessAway() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        long meetingId = enroll(psychologist, dependent, "scheduled");
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());

        mockMvc.perform(delete("/meetings/" + meetingId + "/registration").header("Authorization", fixtures.bearer(dependent)))
                .andExpect(status().isNoContent());

        read("/triages/" + triageId, psychologist).andExpect(status().isNotFound());
        read("/triages?size=100", psychologist).andExpect(jsonPath("$.items.length()").value(0));
    }

    // EXISTS, not a join: a dependent in several meetings of the psychologist comes back once, and a cancelled one
    // among them does not take the access of the others
    @Test
    void aDependentInSeveralMeetingsAppearsOnceAndACancelledOneDoesNotMatter() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);
        enroll(psychologist, dependent, "cancelled");
        enroll(psychologist, dependent, "scheduled");
        enroll(psychologist, dependent, "scheduled");
        long before = triageViews();

        read("/triages?size=100", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(triageId));
        assertThat(triageViews() - before).isEqualTo(1);
        read("/triages/" + triageId, psychologist).andExpect(status().isOk());
    }

    // the count and the pages are about what the psychologist may read, not about every triage
    @Test
    void pagingCountsOnlyWhatThePsychologistMayRead() throws Exception {
        List<Long> visible = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            User dependent = newDependent("Visivel " + i);
            visible.add(submit(dependent));
            enroll(dependent);
            submit(newDependent("Fora " + i));
        }

        read("/triages?size=2&page=0", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(visible.get(2)))
                .andExpect(jsonPath("$.items[1].id").value(visible.get(1)));
        read("/triages?size=2&page=1", psychologist).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(visible.get(0)));
    }

    // the scope is the psychologist's alone: the admin still reads the triage of a dependent who is in no meeting
    @Test
    void theAdminReadsATriageNoPsychologistMayRead() throws Exception {
        User dependent = newDependent(REAL_NAME);
        long triageId = submit(dependent);

        read("/admin/triages/" + triageId, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(triageId));
        read("/triages/" + triageId, psychologist).andExpect(status().isNotFound());
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private ResultActions read(String path, User reader) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.generateToken(reader)));
    }

    // registers the dependent in a meeting of the main psychologist that has not started and was not cancelled
    private long enroll(User dependent) {
        return enroll(psychologist, dependent, "scheduled");
    }

    // a new meeting of `host` with the given status, with the dependent registered in it. Plain SQL on purpose: the
    // status is what the access rule reads
    private long enroll(User host, User dependent, String status) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
                .plus(2L * slot++, ChronoUnit.HOURS);
        long meetingId = fixtures.meeting(host, start, start.plus(1, ChronoUnit.HOURS), status);
        fixtures.register(meetingId, dependent);
        return meetingId;
    }

    private long triageViews() {
        return auditLogRepository.findAll().stream().filter(e -> e.getAction() == AuditAction.TRIAGE_VIEWED).count();
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

    // saveAndFlush: the meeting_registrations rows go in by SQL and their foreign key needs the row to be there
    private User newDependent(String realName) {
        User user = newUser(UserType.dependente, realName);
        Dependent dependent = new Dependent();
        dependent.setNickname(plad.TestNicknames.unique());
        dependent.setUser(user);
        dependentRepository.saveAndFlush(dependent);
        return user;
    }

    // saveAndFlush for the same reason: the meetings go in by SQL and reference psychologists
    private User newPsychologist(String realName) {
        User user = newUser(UserType.psicologo, realName);
        Psychologist row = new Psychologist();
        row.setUser(user);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.saveAndFlush(row);
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
