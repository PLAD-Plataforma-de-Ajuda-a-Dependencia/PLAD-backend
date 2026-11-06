package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Dependents sign up for meetings (decisions 24 and 25). Everything goes through the endpoints; meetings that the
// service would refuse to create (already started) are seeded straight into the table. The race for the last seat
// is MeetingRegistrationConcurrencyTest, which cannot run inside a test transaction.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeetingRegistrationControllerTest {

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
    private User otherPsychologist;
    private User first;
    private User second;
    private User third;
    private User admin;
    private Instant day;

    @BeforeEach
    void setUp() {
        host = newPsychologist("Helena Duarte Anfitria");
        otherPsychologist = newPsychologist("Outra Psicologa");
        first = newDependent("Primeiro Dependente");
        second = newDependent("Segundo Dependente");
        third = newDependent("Terceiro Dependente");
        admin = newUser(UserType.admin, "Admin");
        day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    // ---- registering --------------------------------------------------------------------------------------------

    @Test
    void dependentRegistersAndEveryoneSeesTheCountButOnlyTheyAreFlagged() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(meeting))
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(true))
                .andExpect(jsonPath("$.owned_by_me").value(false));

        // the registered dependent
        send(HttpMethod.GET, "/meetings/" + meeting, first)
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(true));
        // another dependent: the number, not the person
        send(HttpMethod.GET, "/meetings/" + meeting, second)
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(false))
                .andExpect(content().string(not(containsString("nickname"))))
                .andExpect(content().string(not(containsString("real_name"))))
                .andExpect(content().string(not(containsString("Primeiro Dependente"))));
        // the host and an admin: the number, and never "registered by me"
        send(HttpMethod.GET, "/meetings/" + meeting, host)
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(false))
                .andExpect(jsonPath("$.owned_by_me").value(true));
        send(HttpMethod.GET, "/meetings/" + meeting, admin)
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(false));
    }

    @Test
    void listCarriesTheCountAndTheFlagOfEachMeeting() throws Exception {
        long busy = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);
        long empty = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "scheduled", 5);
        register(busy, first);
        register(busy, second);

        ResultActions list = mockMvc.perform(get("/meetings")
                .param("from", day.minus(1, ChronoUnit.HOURS).toString())
                .param("to", day.plus(6, ChronoUnit.HOURS).toString())
                .header("Authorization", "Bearer " + jwtService.generateToken(first)));
        list.andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].registered_count".formatted(busy)).value(2))
                .andExpect(jsonPath("$[?(@.id == %d)].registered_by_me".formatted(busy)).value(true))
                .andExpect(jsonPath("$[?(@.id == %d)].registered_count".formatted(empty)).value(0))
                .andExpect(jsonPath("$[?(@.id == %d)].registered_by_me".formatted(empty)).value(false));
    }

    @Test
    void registeringAgainChangesNothingAndAnswersTheSame() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first).andExpect(status().isOk());
        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_count").value(1))
                .andExpect(jsonPath("$.registered_by_me").value(true));

        assertThat(rowsOf(meeting)).isEqualTo(1);
    }

    // decision 25: the team decided on 2026-11-06 that the triage is not a condition to register
    @Test
    void aDependentWhoNeverSentTheTriageCanRegister() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);
        Integer triages = jdbcTemplate.queryForObject("select count(*) from triages where dependent_id = ?",
                Integer.class, first.getId());
        assertThat(triages).as("the dependent has no triage").isZero();

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_by_me").value(true));
    }

    // the data subject's own action is not a third-party read: no AuditAction (decision 27)
    @Test
    void registeringAndCancellingWriteNoAuditEntry() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first).andExpect(status().isOk());
        send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", first).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where subject_user_id = ? or actor_id = ?",
                Integer.class, first.getId(), first.getId())).isZero();
    }

    @Test
    void theLastSeatIsTakenAndTheNextOneGetsMeetingFull() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 2);

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first).andExpect(status().isOk());
        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_count").value(2));

        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", third)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_FULL"));
        assertThat(rowsOf(meeting)).isEqualTo(2);

        // someone who is already in is not turned away when the meeting is full
        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_count").value(2));
    }

    @Test
    void cancellingFreesTheSeatAndCancellingAgainIsStillNoContent() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 2);
        register(meeting, first);
        register(meeting, second);

        send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", first).andExpect(status().isNoContent());
        assertThat(rowsOf(meeting)).isEqualTo(1);
        send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", first).andExpect(status().isNoContent());
        assertThat(rowsOf(meeting)).isEqualTo(1);
        // never registered: still 204
        send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", third).andExpect(status().isNoContent());

        // the seat is back
        send(HttpMethod.POST, "/meetings/" + meeting + "/registration", third)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered_count").value(2));
    }

    @Test
    void cancellingTheRegistrationOfACancelledMeetingThatHasNotStartedIsAllowed() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "cancelled", 5);
        register(meeting, first);

        send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", first).andExpect(status().isNoContent());
        assertThat(rowsOf(meeting)).isZero();
    }

    // ---- closed meetings ----------------------------------------------------------------------------------------

    @Test
    void registrationInAMeetingThatAlreadyStartedIsRefused() throws Exception {
        Instant now = Instant.now();
        long started = seed(host, now.minus(30, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES), "scheduled", 5);

        send(HttpMethod.POST, "/meetings/" + started + "/registration", first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
        assertThat(rowsOf(started)).isZero();
    }

    @Test
    void registrationInACancelledOrEndedMeetingIsRefused() throws Exception {
        long cancelled = seed(host, day, day.plus(1, ChronoUnit.HOURS), "cancelled", 5);
        Instant now = Instant.now();
        long ended = seed(host, now.minus(3, ChronoUnit.HOURS), now.minus(2, ChronoUnit.HOURS), "ended", 5);

        for (long meeting : new long[]{cancelled, ended}) {
            send(HttpMethod.POST, "/meetings/" + meeting + "/registration", first)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
            assertThat(rowsOf(meeting)).isZero();
        }
    }

    @Test
    void cancellingARegistrationAfterTheMeetingStartedIsRefusedAndKeepsTheRow() throws Exception {
        Instant now = Instant.now();
        long started = seed(host, now.minus(30, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES), "scheduled", 5);
        register(started, first);

        send(HttpMethod.DELETE, "/meetings/" + started + "/registration", first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CONFLICT"));
        assertThat(rowsOf(started)).isEqualTo(1);
    }

    // ---- who may and may not ------------------------------------------------------------------------------------

    @Test
    void psychologistsAndAdminsCannotRegisterOrCancel() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);

        for (User caller : new User[]{host, otherPsychologist, admin}) {
            send(HttpMethod.POST, "/meetings/" + meeting + "/registration", caller)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            send(HttpMethod.DELETE, "/meetings/" + meeting + "/registration", caller)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(rowsOf(meeting)).isZero();
    }

    @Test
    void withoutATokenTheAnswerIs401() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);

        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + meeting + "/registration"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.DELETE, "/meetings/" + meeting + "/registration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownMeetingIs404() throws Exception {
        send(HttpMethod.POST, "/meetings/999999999/registration", first)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        send(HttpMethod.DELETE, "/meetings/999999999/registration", first)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    // ---- editing the capacity -----------------------------------------------------------------------------------

    @Test
    void theHostCannotLowerTheCapacityBelowTheRegistered() throws Exception {
        long meeting = idOf(send(HttpMethod.POST, "/meetings", host,
                body(day, day.plus(1, ChronoUnit.HOURS), 4)).andExpect(status().isCreated()));
        register(meeting, first);
        register(meeting, second);
        register(meeting, third);

        send(HttpMethod.PUT, "/meetings/" + meeting, host, body(day, day.plus(1, ChronoUnit.HOURS), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CAPACITY_TOO_LOW"));
        assertThat(jdbcTemplate.queryForObject("select capacity from meetings where id = ?", Integer.class, meeting))
                .as("the capacity stays").isEqualTo(4);

        // exactly as many seats as registered is fine
        send(HttpMethod.PUT, "/meetings/" + meeting, host, body(day, day.plus(1, ChronoUnit.HOURS), 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.registered_count").value(3));
    }

    @Test
    void theHostResponsesCarryTheCountAndCancellingTheMeetingKeepsTheRegistrations() throws Exception {
        long meeting = idOf(send(HttpMethod.POST, "/meetings", host,
                body(day, day.plus(1, ChronoUnit.HOURS), 4)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.registered_count").value(0))
                .andExpect(jsonPath("$.registered_by_me").value(false)));
        register(meeting, first);

        send(HttpMethod.POST, "/meetings/" + meeting + "/cancel", host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.registered_count").value(1));
        // the rows stay: the dependent still sees what became of the meeting they signed up for
        assertThat(rowsOf(meeting)).isEqualTo(1);
    }

    // ---- data subject rights ------------------------------------------------------------------------------------

    @Test
    void theExportListsTheDependentsRegistrationsWithTitleAndDate() throws Exception {
        long meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled", 5);
        register(meeting, first);
        long other = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "scheduled", 5);
        register(other, second);

        String export = send(HttpMethod.GET, "/me/export", first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dependent.meeting_registrations.length()").value(1))
                .andExpect(jsonPath("$.dependent.meeting_registrations[0].meeting_id").value(meeting))
                .andExpect(jsonPath("$.dependent.meeting_registrations[0].title").value("Semeada"))
                .andExpect(jsonPath("$.dependent.meeting_registrations[0].starts_at").isNotEmpty())
                .andExpect(jsonPath("$.dependent.meeting_registrations[0].registered_at").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(export, "$.dependent.meeting_registrations[0].starts_at")))
                .isEqualTo(day.truncatedTo(ChronoUnit.MILLIS));
        // the other dependent's registration is not in this export (length 1 above), and the host's name is not either
        assertThat(export).doesNotContain("Helena Duarte Anfitria");
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private int rowsOf(long meeting) {
        return jdbcTemplate.queryForObject("select count(*) from meeting_registrations where meeting_id = ?",
                Integer.class, meeting);
    }

    // straight into the table, like a registration that already existed
    private void register(long meeting, User dependent) {
        jdbcTemplate.update("insert into meeting_registrations (meeting_id, dependent_id) values (?, ?)",
                meeting, dependent.getId());
    }

    private long seed(User psychologist, Instant start, Instant end, String status, int capacity) {
        return jdbcTemplate.queryForObject("""
                        insert into meetings (psychologist_id, title, starts_at, ends_at, capacity, status)
                        values (?, 'Semeada', ?, ?, ?, ?) returning id""",
                Long.class, psychologist.getId(), utc(start), utc(end), capacity, status);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String body(Instant start, Instant end, int capacity) {
        return "{\"title\": \"Reuniao de teste\", \"description\": null, \"starts_at\": \"%s\", \"ends_at\": \"%s\", \"capacity\": %d}"
                .formatted(start, end, capacity);
    }

    private static long idOf(ResultActions created) throws Exception {
        return ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions send(HttpMethod method, String path, User user) throws Exception {
        return send(method, path, user, null);
    }

    private ResultActions send(HttpMethod method, String path, User user, String json) throws Exception {
        var builder = request(method, path).header("Authorization", "Bearer " + jwtService.generateToken(user));
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder);
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
