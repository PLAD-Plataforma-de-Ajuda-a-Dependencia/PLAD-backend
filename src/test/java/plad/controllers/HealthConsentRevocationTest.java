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
import plad.config.TestConsents;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.QuestionTriage;
import plad.entity.QuestionType;
import plad.entity.User;
import plad.repositories.AuditLogRepository;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.QuestionTriageRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DELETE /me/consents/health (decision 35; LGPD art. 8, § 5 and art. 18, IX): the dependent withdraws the health data
// consent. The acceptance row stays with revoked_at, the triage and its answers go, the registrations for meetings that
// have not started are cancelled (the others stay), and the triage and the registration ask for the consent again.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class HealthConsentRevocationTest {

    private static final String REVOKE = "/me/consents/health";

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
    private User host;
    private String choiceCode;
    private String choiceOption;
    private String freeTextCode;

    @BeforeEach
    void setUp() {
        fixtures = new LiveRoomFixtures(userRepository, dependentRepository, psychologistRepository, jdbcTemplate, jwtService);
        host = fixtures.psychologist("Psicologa Anfitria");
        List<QuestionTriage> questions = questionTriageRepository.findAllByOrderByDisplayOrderAsc();
        QuestionTriage choice = questions.stream().filter(q -> q.getQuestionType() == QuestionType.single_choice).findFirst().orElseThrow();
        choiceCode = choice.getCode();
        choiceOption = choice.getOptions().get(0).get("code");
        freeTextCode = questions.stream().filter(q -> q.getQuestionType() == QuestionType.free_text).findFirst().orElseThrow().getCode();
    }

    @Test
    void revokingDeletesTheTriageAndItsAnswersAndKeepsTheAcceptanceRowAsProof() throws Exception {
        User dependent = dependentWithConsent();
        long triageId = submitTriage(dependent).andExpect(status().isCreated()).andReturnId();
        assertThat(answerRows(triageId)).isPositive();
        me(dependent).andExpect(jsonPath("$.has_triage").value(true))
                .andExpect(jsonPath("$.health_consent_pending").value(false));

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(count("select count(*) from triages where dependent_id = ?", dependent.getId())).isZero();
        assertThat(answerRows(triageId)).isZero();
        List<Map<String, Object>> health = healthRows(dependent);
        assertThat(health).singleElement().satisfies(row -> {
            assertThat(row.get("version")).isEqualTo(TestConsents.HEALTH_VERSION);
            assertThat(row.get("revoked_at")).isNotNull();
        });
        me(dependent).andExpect(jsonPath("$.has_triage").value(false))
                .andExpect(jsonPath("$.health_consent_pending").value(true));
    }

    @Test
    void revokingCancelsTheRegistrationsOfMeetingsThatHaveNotStartedAndKeepsThePastOnes() throws Exception {
        User dependent = dependentWithConsent();
        Instant now = Instant.now();
        long future = fixtures.meeting(host, now.plus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), "scheduled");
        long futureCancelled = fixtures.meeting(host, now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), "cancelled");
        long started = fixtures.meeting(host, now.minus(10, ChronoUnit.MINUTES), now.plus(50, ChronoUnit.MINUTES), "scheduled");
        long ended = fixtures.meeting(host, now.minus(3, ChronoUnit.DAYS), now.minus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), "ended");
        for (long meeting : List.of(future, futureCancelled, started, ended)) {
            fixtures.register(meeting, dependent);
        }

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(fixtures.isRegistered(future, dependent)).isFalse();
        assertThat(fixtures.isRegistered(futureCancelled, dependent)).isFalse();
        assertThat(fixtures.isRegistered(started, dependent)).isTrue();
        assertThat(fixtures.isRegistered(ended, dependent)).isTrue();
    }

    // only the dependent's own registrations go: another dependent in the same meeting keeps the seat
    @Test
    void revokingTouchesNoOtherDependent() throws Exception {
        User dependent = dependentWithConsent();
        User other = dependentWithConsent();
        long otherTriage = submitTriage(other).andExpect(status().isCreated()).andReturnId();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        long meeting = fixtures.meeting(host, start, start.plus(1, ChronoUnit.HOURS), "scheduled");
        fixtures.register(meeting, dependent);
        fixtures.register(meeting, other);

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(fixtures.isRegistered(meeting, other)).isTrue();
        assertThat(count("select count(*) from triages where id = ?", otherTriage)).isOne();
        assertThat(healthRows(other)).singleElement().satisfies(row -> assertThat(row.get("revoked_at")).isNull());
    }

    @Test
    void thePsychologistNoLongerSeesTheTriage() throws Exception {
        User dependent = dependentWithConsent();
        long triageId = submitTriage(dependent).andExpect(status().isCreated()).andReturnId();
        // a meeting that already ended keeps the registration, and with it the 30-day access; the triage is gone anyway
        Instant end = Instant.now().minus(1, ChronoUnit.DAYS);
        long meeting = fixtures.meeting(host, end.minus(1, ChronoUnit.HOURS), end, "ended");
        fixtures.register(meeting, dependent);
        readAsHost("/triages/" + triageId).andExpect(status().isOk());
        long viewsBefore = triageViews();

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(fixtures.isRegistered(meeting, dependent)).isTrue();
        readAsHost("/triages/" + triageId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIAGE_NOT_FOUND"));
        readAsHost("/triages?size=100").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(triageViews()).isEqualTo(viewsBefore);
    }

    @Test
    void revokingAgainIsNoContentAndChangesNothing() throws Exception {
        User dependent = dependentWithConsent();
        revoke(dependent).andExpect(status().isNoContent());
        Timestamp firstRevocation = (Timestamp) healthRows(dependent).getFirst().get("revoked_at");

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(healthRows(dependent)).singleElement()
                .satisfies(row -> assertThat(row.get("revoked_at")).isEqualTo(firstRevocation));
    }

    // a dependent who never accepted has nothing to withdraw: still 204, and nothing is written
    @Test
    void revokingWithoutAConsentOnFileIsNoContent() throws Exception {
        User dependent = fixtures.dependent("Sem Aceite");

        revoke(dependent).andExpect(status().isNoContent());

        assertThat(healthRows(dependent)).isEmpty();
    }

    @Test
    void psychologistAndAdminAreForbiddenAndNoTokenIsUnauthorized() throws Exception {
        revoke(host).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        revoke(fixtures.admin()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete(REVOKE)).andExpect(status().isUnauthorized());
    }

    @Test
    void afterRevokingTheTriageAndTheRegistrationAskForTheConsentAgain() throws Exception {
        User dependent = dependentWithConsent();
        submitTriage(dependent).andExpect(status().isCreated());
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        long meeting = fixtures.meeting(host, start, start.plus(1, ChronoUnit.HOURS), "scheduled");

        revoke(dependent).andExpect(status().isNoContent());

        submitTriage(dependent).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        registerFor(meeting, dependent).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        assertThat(fixtures.isRegistered(meeting, dependent)).isFalse();

        // accepting the same version again adds a new row next to the revoked one, and the doors open again
        mockMvc.perform(post("/me/consents/health").header("Authorization", fixtures.bearer(dependent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted_health_version\": \"%s\"}".formatted(TestConsents.HEALTH_VERSION)))
                .andExpect(status().isNoContent());
        assertThat(healthRows(dependent)).extracting(row -> row.get("revoked_at") == null)
                .containsExactly(false, true);
        me(dependent).andExpect(jsonPath("$.health_consent_pending").value(false));
        submitTriage(dependent).andExpect(status().isCreated());
        registerFor(meeting, dependent).andExpect(status().isOk());
    }

    @Test
    void theExportShowsTheRevocation() throws Exception {
        User dependent = dependentWithConsent();
        submitTriage(dependent).andExpect(status().isCreated());

        revoke(dependent).andExpect(status().isNoContent());

        mockMvc.perform(get("/me/export").header("Authorization", fixtures.bearer(dependent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consents[?(@.document == 'health')].revoked_at").isNotEmpty())
                .andExpect(jsonPath("$.consents[?(@.document == 'health')].version").value(TestConsents.HEALTH_VERSION))
                .andExpect(jsonPath("$.triage").isEmpty());
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private User dependentWithConsent() {
        User dependent = fixtures.dependent("Titular Que Retira");
        TestConsents.grantHealth(jdbcTemplate, dependent.getId());
        return dependent;
    }

    private ResultActions revoke(User user) throws Exception {
        return mockMvc.perform(delete(REVOKE).header("Authorization", fixtures.bearer(user)));
    }

    private ResultActions me(User user) throws Exception {
        return mockMvc.perform(get("/me").header("Authorization", fixtures.bearer(user))).andExpect(status().isOk());
    }

    private ResultActions readAsHost(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", fixtures.bearer(host)));
    }

    private ResultActions registerFor(long meetingId, User dependent) throws Exception {
        return mockMvc.perform(post("/meetings/" + meetingId + "/registration")
                .header("Authorization", fixtures.bearer(dependent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestConsents.participationBody()));
    }

    private TriageCall submitTriage(User dependent) throws Exception {
        String body = "{\"answers\": [{\"question_code\": \"%s\", \"values\": [\"%s\"]}, {\"question_code\": \"%s\", \"values\": [\"texto livre\"]}]}"
                .formatted(choiceCode, choiceOption, freeTextCode);
        return new TriageCall(mockMvc.perform(post("/triage")
                .header("Authorization", fixtures.bearer(dependent))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)));
    }

    // the POST /triage result, with the id of the stored triage at hand
    private record TriageCall(ResultActions result) {
        TriageCall andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            result.andExpect(matcher);
            return this;
        }

        long andReturnId() throws Exception {
            return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        }
    }

    private List<Map<String, Object>> healthRows(User user) {
        return jdbcTemplate.queryForList("select version, revoked_at from consents where user_id = ? and document = 'health' "
                + "order by id", user.getId());
    }

    private long answerRows(long triageId) {
        return count("select count(*) from result_questions_triages where triage_id = ?", triageId);
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private long triageViews() {
        return auditLogRepository.findAll().stream().filter(e -> e.getAction() == AuditAction.TRIAGE_VIEWED).count();
    }
}
