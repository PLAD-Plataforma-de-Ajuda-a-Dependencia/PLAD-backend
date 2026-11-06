package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RF012: every rule goes through the endpoints. Times are relative to the real clock with a minute of margin
// (59 vs 61 minutes); the exact 60-minute edge is in MeetingRulesTest, which has a fixed clock.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeetingControllerTest {

    private static final String ISO_UTC = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";
    private static final String HOST_NAME = "Helena Duarte Anfitria";

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

    private User host;
    private User dependent;
    private User admin;
    private Instant day; // a slot well ahead: tomorrow + 2 days, on the hour

    @BeforeEach
    void setUp() {
        host = newPsychologist(HOST_NAME);
        dependent = newDependent("Dependente Leitor");
        admin = newUser(UserType.admin, "Admin Leitor");
        day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    // ---- create -------------------------------------------------------------------------------------------------

    @Test
    void psychologistCreatesAMeetingAndGetsItBackWithHostAndOwnership() throws Exception {
        Instant start = day;
        Instant end = day.plus(1, ChronoUnit.HOURS);

        String response = send(HttpMethod.POST, "/meetings", host, body("Grupo de apoio", "Roda semanal", start, end, 12))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/meetings/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Grupo de apoio"))
                .andExpect(jsonPath("$.description").value("Roda semanal"))
                .andExpect(jsonPath("$.capacity").value(12))
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.host").value(HOST_NAME))
                .andExpect(jsonPath("$.owned_by_me").value(true))
                .andExpect(jsonPath("$.starts_at", matchesPattern(ISO_UTC)))
                .andExpect(jsonPath("$.ends_at", matchesPattern(ISO_UTC)))
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(response, "$.starts_at"))).isEqualTo(start);
        assertThat(Instant.parse(JsonPath.read(response, "$.ends_at"))).isEqualTo(end);
        assertThat(response).doesNotContain(host.getEmail()).doesNotContain("email");
    }

    @Test
    void descriptionIsOptionalAndBlankBecomesNull() throws Exception {
        send(HttpMethod.POST, "/meetings", host, body("Sem descricao", null, day, day.plus(1, ChronoUnit.HOURS), 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value((Object) null));
        send(HttpMethod.POST, "/meetings", host, body("Descricao em branco", "   ", day.plus(3, ChronoUnit.HOURS),
                day.plus(4, ChronoUnit.HOURS), 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value((Object) null));
    }

    @Test
    void durationLimitsHoldAtBothEnds() throws Exception {
        assertInvalid(createAs(newPsychologist("P14"), day, day.plus(14, ChronoUnit.MINUTES), 5), "ends_at");
        createAs(newPsychologist("P15"), day, day.plus(15, ChronoUnit.MINUTES), 5).andExpect(status().isCreated());
        createAs(newPsychologist("P180"), day, day.plus(180, ChronoUnit.MINUTES), 5).andExpect(status().isCreated());
        assertInvalid(createAs(newPsychologist("P181"), day, day.plus(181, ChronoUnit.MINUTES), 5), "ends_at");
    }

    @Test
    void capacityLimitsHoldAtBothEnds() throws Exception {
        Instant end = day.plus(1, ChronoUnit.HOURS);
        assertInvalid(createAs(newPsychologist("C1"), day, end, 1), "capacity");
        createAs(newPsychologist("C2"), day, end, 2).andExpect(status().isCreated());
        createAs(newPsychologist("C30"), day, end, 30).andExpect(status().isCreated());
        assertInvalid(createAs(newPsychologist("C31"), day, end, 31), "capacity");
    }

    @Test
    void leadTimeOf60MinutesIsEnforcedOnCreation() throws Exception {
        Instant soon = Instant.now().plus(59, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        assertInvalid(createAs(newPsychologist("L59"), soon, soon.plus(1, ChronoUnit.HOURS), 5), "starts_at");

        Instant enough = Instant.now().plus(61, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        createAs(newPsychologist("L61"), enough, enough.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated());

        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        assertInvalid(createAs(newPsychologist("Lpast"), past, past.plus(1, ChronoUnit.HOURS), 5), "starts_at");
    }

    @Test
    void endNotAfterStartIsInvalid() throws Exception {
        assertInvalid(createAs(newPsychologist("E0"), day, day, 5), "ends_at");
        assertInvalid(createAs(newPsychologist("Eneg"), day, day.minus(30, ChronoUnit.MINUTES), 5), "ends_at");
    }

    @Test
    void everyBrokenRuleIsReportedTogetherWithItsField() throws Exception {
        Instant soon = Instant.now().plus(10, ChronoUnit.MINUTES);
        createAs(host, soon, soon.plus(5, ChronoUnit.MINUTES), 99)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEETING"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'starts_at')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'ends_at')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'capacity')]").exists())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("capacity: ")));
    }

    @Test
    void shapeErrorsAreValidationErrors() throws Exception {
        Instant end = day.plus(1, ChronoUnit.HOURS);
        send(HttpMethod.POST, "/meetings", host, body("", null, day, end, 5)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        send(HttpMethod.POST, "/meetings", host, body("x".repeat(121), null, day, end, 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        send(HttpMethod.POST, "/meetings", host, body("x".repeat(120), null, day, end, 5))
                .andExpect(status().isCreated());
        send(HttpMethod.POST, "/meetings", host, "{\"title\": \"Sem datas\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- overlap -----------------------------------------------------------------------------------------------

    @Test
    void overlappingMeetingsOfTheSamePsychologistAreAConflict() throws Exception {
        createAs(host, day, day.plus(2, ChronoUnit.HOURS), 5).andExpect(status().isCreated());

        // same slot, starts inside, ends inside, contains it
        createAs(host, day, day.plus(2, ChronoUnit.HOURS), 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
        createAs(host, day.plus(1, ChronoUnit.HOURS), day.plus(3, ChronoUnit.HOURS), 5).andExpect(status().isConflict());
        createAs(host, day.minus(1, ChronoUnit.HOURS), day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isConflict());
        // 3 hours (the maximum) wrapping the 2-hour one
        createAs(host, day.minus(30, ChronoUnit.MINUTES), day.plus(150, ChronoUnit.MINUTES), 5).andExpect(status().isConflict());
    }

    @Test
    void touchingMeetingsDoNotOverlap() throws Exception {
        // one ends at 19h and the next starts at 19h
        Instant nineteen = day;
        createAs(host, nineteen.minus(1, ChronoUnit.HOURS), nineteen, 5).andExpect(status().isCreated());
        createAs(host, nineteen, nineteen.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated());
    }

    @Test
    void anotherPsychologistMayHostAtTheSameTime() throws Exception {
        createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated());
        createAs(newPsychologist("Colega"), day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated());
    }

    @Test
    void aCancelledMeetingFreesItsSlot() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        send(HttpMethod.POST, "/meetings/" + id + "/cancel", host, null).andExpect(status().isOk());

        createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated());
    }

    // the database enforces it too (a race between two requests would get here): V25
    @Test
    void theDatabaseRejectsOverlappingScheduledMeetingsEvenWithoutTheService() {
        seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled");

        assertThatThrownBy(() -> seed(host, day.plus(30, ChronoUnit.MINUTES), day.plus(90, ChronoUnit.MINUTES), "scheduled"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("meetings_no_overlap");
    }

    // ---- who may do what ---------------------------------------------------------------------------------------

    @Test
    void dependentAndAdminCannotCreateOrChangeMeetings() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        String body = body("Qualquer", null, day.plus(5, ChronoUnit.HOURS), day.plus(6, ChronoUnit.HOURS), 5);

        for (User user : List.of(dependent, admin)) {
            send(HttpMethod.POST, "/meetings", user, body).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            send(HttpMethod.PUT, "/meetings/" + id, user, body).andExpect(status().isForbidden());
            send(HttpMethod.POST, "/meetings/" + id + "/cancel", user, null).andExpect(status().isForbidden());
            send(HttpMethod.POST, "/meetings/" + id + "/end", user, null).andExpect(status().isForbidden());
        }
    }

    @Test
    void anotherPsychologistCannotEditCancelOrEndSomeoneElsesMeeting() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        User other = newPsychologist("Outro Psicologo");
        String body = body("Sequestro", null, day, day.plus(1, ChronoUnit.HOURS), 5);

        send(HttpMethod.PUT, "/meetings/" + id, other, body).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        send(HttpMethod.POST, "/meetings/" + id + "/cancel", other, null).andExpect(status().isForbidden());
        send(HttpMethod.POST, "/meetings/" + id + "/end", other, null).andExpect(status().isForbidden());

        send(HttpMethod.GET, "/meetings/" + id, host, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Reuniao de teste"))
                .andExpect(jsonPath("$.status").value("scheduled"));
    }

    @Test
    void withoutTokenEverythingIsUnauthorized() throws Exception {
        mockMvc.perform(get("/meetings").param("from", day.toString()).param("to", day.plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/meetings/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.POST, "/meetings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- edit --------------------------------------------------------------------------------------------------

    @Test
    void hostEditsAScheduledMeetingBeforeItStarts() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        Instant newStart = day.plus(3, ChronoUnit.HOURS);

        send(HttpMethod.PUT, "/meetings/" + id, host,
                body("Novo titulo", "Nova descricao", newStart, newStart.plus(90, ChronoUnit.MINUTES), 20))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Novo titulo"))
                .andExpect(jsonPath("$.description").value("Nova descricao"))
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.status").value("scheduled"));
    }

    @Test
    void editingKeepsTheSameRulesAsCreating() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));

        assertInvalid(send(HttpMethod.PUT, "/meetings/" + id, host,
                body("T", null, day, day.plus(200, ChronoUnit.MINUTES), 5)), "ends_at");
        assertInvalid(send(HttpMethod.PUT, "/meetings/" + id, host,
                body("T", null, day, day.plus(1, ChronoUnit.HOURS), 31)), "capacity");
        Instant soon = Instant.now().plus(10, ChronoUnit.MINUTES);
        assertInvalid(send(HttpMethod.PUT, "/meetings/" + id, host,
                body("T", null, soon, soon.plus(1, ChronoUnit.HOURS), 5)), "starts_at");
    }

    @Test
    void editingCannotCreateAnOverlapButMayKeepItsOwnSlot() throws Exception {
        long first = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        createAs(host, day.plus(2, ChronoUnit.HOURS), day.plus(3, ChronoUnit.HOURS), 5).andExpect(status().isCreated());

        // its own slot is not an overlap with itself
        send(HttpMethod.PUT, "/meetings/" + first, host, body("Mesmo horario", null, day, day.plus(1, ChronoUnit.HOURS), 6))
                .andExpect(status().isOk());
        // stretching it into the next one is
        send(HttpMethod.PUT, "/meetings/" + first, host, body("Invade", null, day, day.plus(150, ChronoUnit.MINUTES), 6))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
    }

    @Test
    void aMeetingThatAlreadyStartedCannotBeEdited() throws Exception {
        long id = seed(host, Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().plus(50, ChronoUnit.MINUTES), "scheduled");

        send(HttpMethod.PUT, "/meetings/" + id, host, body("Tarde demais", null, day, day.plus(1, ChronoUnit.HOURS), 5))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
    }

    @Test
    void aCancelledOrEndedMeetingCannotBeEdited() throws Exception {
        long cancelled = seed(host, day, day.plus(1, ChronoUnit.HOURS), "cancelled");
        long ended = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "ended");
        String body = body("Reabrir", null, day.plus(6, ChronoUnit.HOURS), day.plus(7, ChronoUnit.HOURS), 5);

        send(HttpMethod.PUT, "/meetings/" + cancelled, host, body).andExpect(status().isConflict());
        send(HttpMethod.PUT, "/meetings/" + ended, host, body).andExpect(status().isConflict());
    }

    @Test
    void theLeadTimeOnlyAppliesWhenTheStartMoves() throws Exception {
        // starts in 30 minutes: it could not be created now, but its title can still be fixed
        Instant start = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        long id = seed(host, start, start.plus(1, ChronoUnit.HOURS), "scheduled");

        send(HttpMethod.PUT, "/meetings/" + id, host, body("Titulo corrigido", null, start, start.plus(1, ChronoUnit.HOURS), 8))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Titulo corrigido"));

        Instant moved = start.plus(5, ChronoUnit.MINUTES);
        assertInvalid(send(HttpMethod.PUT, "/meetings/" + id, host,
                body("Titulo corrigido", null, moved, moved.plus(1, ChronoUnit.HOURS), 8)), "starts_at");
    }

    // ---- cancel / end ------------------------------------------------------------------------------------------

    @Test
    void hostCancelsAScheduledMeetingAndItStaysListedAsCancelled() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));

        send(HttpMethod.POST, "/meetings/" + id + "/cancel", host, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
        send(HttpMethod.POST, "/meetings/" + id + "/cancel", host, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));

        listAs(dependent, day.minus(1, ChronoUnit.DAYS), day.plus(1, ChronoUnit.DAYS)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].status".formatted(id)).value("cancelled"));
        assertThat(jdbcTemplate.queryForObject("select cancelled_at is not null from meetings where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void aMeetingInProgressCanStillBeCancelledButOneThatEndedCannot() throws Exception {
        long inProgress = seed(host, Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().plus(50, ChronoUnit.MINUTES), "scheduled");
        long over = seed(host, Instant.now().minus(3, ChronoUnit.HOURS), Instant.now().minus(2, ChronoUnit.HOURS), "scheduled");

        send(HttpMethod.POST, "/meetings/" + inProgress + "/cancel", host, null).andExpect(status().isOk());
        send(HttpMethod.POST, "/meetings/" + over + "/cancel", host, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
    }

    @Test
    void hostEndsAMeetingOnlyAfterItStarted() throws Exception {
        long future = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        long inProgress = seed(host, Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().plus(50, ChronoUnit.MINUTES), "scheduled");

        send(HttpMethod.POST, "/meetings/" + future + "/end", host, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
        send(HttpMethod.POST, "/meetings/" + inProgress + "/end", host, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"));
        send(HttpMethod.POST, "/meetings/" + inProgress + "/end", host, null).andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("select ended_at is not null from meetings where id = ?", Boolean.class, inProgress)).isTrue();
    }

    @Test
    void aCancelledMeetingCannotBeEnded() throws Exception {
        long id = seed(host, Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().plus(50, ChronoUnit.MINUTES), "cancelled");

        send(HttpMethod.POST, "/meetings/" + id + "/end", host, null).andExpect(status().isConflict());
    }

    @Test
    void unknownMeetingIsNotFound() throws Exception {
        String body = body("Qualquer", null, day, day.plus(1, ChronoUnit.HOURS), 5);

        send(HttpMethod.GET, "/meetings/999999999", dependent, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        send(HttpMethod.PUT, "/meetings/999999999", host, body).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/meetings/999999999/cancel", host, null).andExpect(status().isNotFound());
        send(HttpMethod.POST, "/meetings/999999999/end", host, null).andExpect(status().isNotFound());
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    @Test
    void everyRoleReadsTheCalendarOrderedByStartAndOwnershipIsPerCaller() throws Exception {
        User colleague = newPsychologist("Colega Anfitriao");
        long late = idOf(createAs(host, day.plus(5, ChronoUnit.HOURS), day.plus(6, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        long early = idOf(createAs(colleague, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        long middle = idOf(createAs(host, day.plus(2, ChronoUnit.HOURS), day.plus(3, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));
        Instant from = day.minus(1, ChronoUnit.DAYS);
        Instant to = day.plus(1, ChronoUnit.DAYS);

        for (User reader : List.of(dependent, admin, host)) {
            String response = listAs(reader, from, to).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Integer> ids = JsonPath.read(response, "$[*].id");
            assertThat(ids).containsExactly((int) early, (int) middle, (int) late);
            assertThat(response).doesNotContain(host.getEmail()).doesNotContain(colleague.getEmail()).doesNotContain("email");
        }
        listAs(host, from, to)
                .andExpect(jsonPath("$[?(@.id == %d)].owned_by_me".formatted(late)).value(true))
                .andExpect(jsonPath("$[?(@.id == %d)].owned_by_me".formatted(early)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].host".formatted(early)).value("Colega Anfitriao"));
        listAs(dependent, from, to)
                .andExpect(jsonPath("$[?(@.id == %d)].owned_by_me".formatted(late)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].host".formatted(late)).value(HOST_NAME));
    }

    @Test
    void theWindowIsHalfOpenOnTheStart() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));

        listAs(dependent, day, day.plus(1, ChronoUnit.DAYS)).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).exists());
        listAs(dependent, day.minus(1, ChronoUnit.DAYS), day).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).isEmpty());
    }

    @Test
    void theWindowIsAtMost62Days() throws Exception {
        Instant from = day;
        listAs(dependent, from, from.plus(62, ChronoUnit.DAYS)).andExpect(status().isOk());

        listAs(dependent, from, from.plus(62, ChronoUnit.DAYS).plusSeconds(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEETING"))
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        listAs(dependent, from, from.plus(63, ChronoUnit.DAYS)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEETING"));
    }

    @Test
    void theWindowNeedsBothEndsInOrder() throws Exception {
        listAs(dependent, day, day).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEETING"));
        listAs(dependent, day, day.minus(1, ChronoUnit.DAYS)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEETING"));
        send(HttpMethod.GET, "/meetings", dependent, null).andExpect(status().isBadRequest());
        mockMvc.perform(get("/meetings").param("from", "ontem").param("to", day.toString())
                        .header("Authorization", "Bearer " + jwtService.generateToken(dependent)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDependentReadsOneMeetingWithoutAnyEmail() throws Exception {
        long id = idOf(createAs(host, day, day.plus(1, ChronoUnit.HOURS), 5).andExpect(status().isCreated()));

        String body = send(HttpMethod.GET, "/meetings/" + id, dependent, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value(HOST_NAME))
                .andExpect(jsonPath("$.owned_by_me").value(false))
                .andExpect(jsonPath("$", not(hasKey("email"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(host.getEmail());
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private ResultActions createAs(User user, Instant start, Instant end, int capacity) throws Exception {
        return send(HttpMethod.POST, "/meetings", user, body("Reuniao de teste", null, start, end, capacity));
    }

    private ResultActions listAs(User reader, Instant from, Instant to) throws Exception {
        return mockMvc.perform(get("/meetings").param("from", from.toString()).param("to", to.toString())
                .header("Authorization", "Bearer " + jwtService.generateToken(reader)));
    }

    private ResultActions send(HttpMethod method, String path, User user, String json) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path)
                .header("Authorization", "Bearer " + jwtService.generateToken(user));
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder);
    }

    private static void assertInvalid(ResultActions result, String field) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEETING"))
                .andExpect(jsonPath("$.errors[?(@.field == '%s')]".formatted(field)).exists());
    }

    private static long idOf(ResultActions created) throws Exception {
        return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String body(String title, String description, Instant start, Instant end, int capacity) {
        return "{\"title\": \"%s\", \"description\": %s, \"starts_at\": \"%s\", \"ends_at\": \"%s\", \"capacity\": %d}"
                .formatted(title, description == null ? "null" : "\"" + description + "\"", start, end, capacity);
    }

    // straight into the table: the service would refuse a meeting that already started
    private long seed(User psychologist, Instant start, Instant end, String status) {
        return jdbcTemplate.queryForObject("""
                        insert into meetings (psychologist_id, title, starts_at, ends_at, capacity, status)
                        values (?, 'Semeada', ?, ?, 10, ?) returning id""",
                Long.class, psychologist.getId(), utc(start), utc(end), status);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private User newPsychologist(String realName) {
        User user = newUser(UserType.psicologo, realName);
        Psychologist row = new Psychologist();
        row.setUser(user);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.save(row);
        return user;
    }

    private User newDependent(String realName) {
        User user = newUser(UserType.dependente, realName);
        Dependent row = new Dependent();
        row.setNickname(plad.TestNicknames.unique());
        row.setUser(user);
        dependentRepository.save(row);
        return user;
    }

    private User newUser(UserType type, String realName) {
        User user = new User();
        user.setRealName(realName);
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.save(user);
    }
}
