package plad.controllers;

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
import plad.services.room.MeetingIdentity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /meetings/{id}/participants (decisions 26 and 27): only the host reads it, with nickname and real name, and
// every dependent listed gets an audit entry in the same transaction, which they see in GET /me/access-log.
// The audit table takes no deletes, so the test is @Transactional (rolled back) and filters on its own meeting ids.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeetingParticipantsTest {

    private static final String AUDIT_SQL = """
            select subject_user_id, actor_id, entity, entity_id, details::text as details
            from audit_log where action = 'MEETING_PARTICIPANTS_VIEWED' and entity_id = ? order by id""";

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
    @Autowired
    private MeetingIdentity meetingIdentity;

    private User host;
    private User otherPsychologist;
    private User ana;
    private User bruno;
    private User outsider; // a dependent who is not in the meeting
    private User admin;
    private Instant day;
    private long meeting;

    @BeforeEach
    void setUp() {
        host = newPsychologist("Helena Duarte Anfitria");
        otherPsychologist = newPsychologist("Outra Psicologa");
        ana = newDependent("Ana Pereira Real");
        bruno = newDependent("Bruno Souza Real");
        outsider = newDependent("Carla Fora Real");
        admin = newUser(UserType.admin, "Admin");
        day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        meeting = seed(host, day, day.plus(1, ChronoUnit.HOURS), "scheduled");
        // explicit instants so the order does not depend on now() (the same inside a test transaction)
        register(meeting, ana, day.minus(10, ChronoUnit.HOURS));
        register(meeting, bruno, day.minus(5, ChronoUnit.HOURS));
    }

    @Test
    void theHostGetsNicknameAndRealNameOfEachRegisteredDependentAndOneAuditEntryEach() throws Exception {
        String anaNickname = nicknameOf(ana);
        String brunoNickname = nicknameOf(bruno);

        String response = get("/meetings/" + meeting + "/participants", host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nickname").value(anaNickname))
                .andExpect(jsonPath("$[0].real_name").value("Ana Pereira Real"))
                .andExpect(jsonPath("$[0].registered_at").isNotEmpty())
                .andExpect(jsonPath("$[1].nickname").value(brunoNickname))
                .andExpect(jsonPath("$[1].real_name").value("Bruno Souza Real"))
                // the identity each one has in this meeting's video room, so the host can match the person on screen
                .andExpect(jsonPath("$[0].identity").value(meetingIdentity.of(meeting, ana.getId())))
                .andExpect(jsonPath("$[1].identity").value(meetingIdentity.of(meeting, bruno.getId())))
                // only these four keys: no user id, no e-mail
                .andExpect(jsonPath("$[0].length()").value(4))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(ana.getEmail()).doesNotContain(bruno.getEmail())
                .doesNotContain("Carla Fora Real")
                // the identity does not carry the user id
                .doesNotContain("\"user_id\"")
                .doesNotContain(meetingIdentity.of(meeting, outsider.getId()));

        List<Map<String, Object>> entries = jdbcTemplate.queryForList(AUDIT_SQL, meeting);
        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(e -> ((Number) e.get("subject_user_id")).longValue())
                .containsExactlyInAnyOrder(ana.getId(), bruno.getId());
        for (Map<String, Object> entry : entries) {
            assertThat(((Number) entry.get("actor_id")).longValue()).isEqualTo(host.getId());
            assertThat(entry.get("entity")).isEqualTo("MEETING");
            // ids and the role only: not the nickname, not the name
            String details = (String) entry.get("details");
            assertThat(details).contains("\"meeting_id\"").contains("\"actor_role\"").contains("psicologo");
            assertThat(details).doesNotContain("Ana").doesNotContain("Bruno").doesNotContain("Helena")
                    .doesNotContain(anaNickname).doesNotContain(brunoNickname);
        }
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log where action = 'MEETING_PARTICIPANTS_VIEWED' and entity_id = ?
                and (details->>'meeting_id')::bigint = ? and details->>'actor_role' = 'psicologo'""",
                Integer.class, meeting, meeting)).isEqualTo(2);
    }

    @Test
    void everyReadLeavesItsOwnEntriesSoReadingTwiceLeavesFour() throws Exception {
        get("/meetings/" + meeting + "/participants", host).andExpect(status().isOk());
        get("/meetings/" + meeting + "/participants", host).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForList(AUDIT_SQL, meeting)).hasSize(4);
    }

    @Test
    void theDependentSeesTheReadInTheirAccessLogWithTheRoleAndNeverWho() throws Exception {
        get("/meetings/" + meeting + "/participants", host).andExpect(status().isOk());

        get("/me/access-log", ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("MEETING_PARTICIPANTS_VIEWED"))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"))
                .andExpect(jsonPath("$.items[0].occurred_at").isNotEmpty())
                .andExpect(jsonPath("$.items[0].actor_id").doesNotExist())
                .andExpect(jsonPath("$.items[0].details").doesNotExist())
                .andExpect(content().string(not(containsString("Helena"))));
        get("/me/access-log", bruno)
                .andExpect(jsonPath("$.total_elements").value(1));
        // not listed, nothing to see
        get("/me/access-log", outsider)
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void anotherPsychologistADependentAndAnAdminAreForbiddenAndLeaveNoEntry() throws Exception {
        for (User caller : new User[]{otherPsychologist, ana, outsider, admin}) {
            get("/meetings/" + meeting + "/participants", caller)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(content().string(not(containsString("Ana Pereira Real"))));
        }
        assertThat(jdbcTemplate.queryForList(AUDIT_SQL, meeting)).isEmpty();
    }

    @Test
    void withoutATokenTheAnswerIs401() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/meetings/" + meeting + "/participants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aMeetingWithNobodyRegisteredGivesAnEmptyListAndNoEntry() throws Exception {
        long empty = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "scheduled");

        get("/meetings/" + empty + "/participants", host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(jdbcTemplate.queryForList(AUDIT_SQL, empty)).isEmpty();
    }

    @Test
    void theHostStillReadsTheListOfACancelledMeeting() throws Exception {
        long cancelled = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "cancelled");
        register(cancelled, ana, day);

        get("/meetings/" + cancelled + "/participants", host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].real_name").value("Ana Pereira Real"));
        assertThat(jdbcTemplate.queryForList(AUDIT_SQL, cancelled)).hasSize(1);
    }

    @Test
    void anUnknownMeetingIs404() throws Exception {
        get("/meetings/999999999/participants", host)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    // the whole path through the endpoints: a dependent registers, the host sees them, the dependent sees the read
    @Test
    void aRegistrationMadeThroughTheEndpointShowsUpInTheHostListAndLeavesAnEntry() throws Exception {
        long fresh = seed(host, day.plus(3, ChronoUnit.HOURS), day.plus(4, ChronoUnit.HOURS), "scheduled");

        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + fresh + "/registration")
                        .header("Authorization", "Bearer " + jwtService.generateToken(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestConsents.participationBody()))
                .andExpect(status().isOk());

        get("/meetings/" + fresh + "/participants", host)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nickname").value(nicknameOf(outsider)))
                .andExpect(jsonPath("$[0].real_name").value("Carla Fora Real"));

        get("/me/access-log", outsider)
                .andExpect(jsonPath("$.items[0].action").value("MEETING_PARTICIPANTS_VIEWED"))
                .andExpect(jsonPath("$.items[0].actor_role").value("psicologo"));
    }

    // ---- fixtures -----------------------------------------------------------------------------------------------

    private ResultActions get(String path, User user) throws Exception {
        return mockMvc.perform(request(HttpMethod.GET, path)
                .header("Authorization", "Bearer " + jwtService.generateToken(user)));
    }

    private String nicknameOf(User dependent) {
        return jdbcTemplate.queryForObject("select nickname from dependents where user_id = ?", String.class,
                dependent.getId());
    }

    private void register(long meetingId, User dependent, Instant at) {
        jdbcTemplate.update("insert into meeting_registrations (meeting_id, dependent_id, registered_at) values (?, ?, ?)",
                meetingId, dependent.getId(), utc(at));
    }

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
        // the registration made through the endpoint in one of the tests needs the health data consent
        TestConsents.grantHealth(jdbcTemplate, user.getId());
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
