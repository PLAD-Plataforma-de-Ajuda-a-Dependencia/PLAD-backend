package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.User;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;
import plad.services.room.FakeMeetingRoomGateway;
import plad.services.room.MeetingIdentity;
import plad.services.room.MeetingRoomGateway;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /meetings/{id}/join (RF005, RF007): who may enter and when, and what the token tells the video provider. The
// token is decoded here with the test API secret (surefire sets one): it must carry the room, the right permissions,
// the nickname and the role, and no real name, e-mail or user id. Nothing here reaches LiveKit.
// Real clock with a minute of margin, like the other meeting tests; the exact edges are in MeetingRulesTest.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeetingJoinTest {

    private static final Duration HOUR = Duration.ofHours(1);

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
    @Autowired
    private MeetingRoomGateway gateway;
    @Value("${livekit.api-secret}")
    private String apiSecret;
    @Value("${livekit.api-key}")
    private String apiKey;

    private LiveRoomFixtures fixtures;
    private User host;
    private User otherPsychologist;
    private User ana;     // registered
    private User bruno;   // registered
    private User outsider; // a dependent who is not in the meeting
    private User admin;
    private long live;    // started 5 minutes ago, ends in 55

    @BeforeEach
    void setUp() {
        fixtures = new LiveRoomFixtures(userRepository, dependentRepository, psychologistRepository, jdbcTemplate, jwtService);
        host = fixtures.psychologist("Helena Duarte Anfitria");
        otherPsychologist = fixtures.psychologist("Outra Psicologa");
        ana = fixtures.dependent("Ana Pereira Real");
        bruno = fixtures.dependent("Bruno Souza Real");
        outsider = fixtures.dependent("Carla Fora Real");
        admin = fixtures.admin();
        Instant now = Instant.now();
        live = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "scheduled");
        fixtures.register(live, ana);
        fixtures.register(live, bruno);
    }

    // ---- who ------------------------------------------------------------------------------------------------------

    @Test
    void theHostEntersAndPublishesEverythingAndAdministersTheRoom() throws Exception {
        String token = join(live, host).andExpect(status().isOk())
                .andExpect(jsonPath("$.server_url").value("ws://localhost:7880"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        Claims claims = decode(JsonPath.read(token, "$.token"));

        assertThat(claims.getSubject()).isEqualTo(meetingIdentity.of(live, host.getId()));
        assertThat(claims.getIssuer()).isEqualTo(apiKey);
        assertThat(claims.get("name")).isEqualTo("Helena Duarte Anfitria"); // the name the calendar already shows
        assertThat(claims.get("metadata")).isEqualTo("{\"role\":\"psicologo\"}");
        Map<?, ?> video = video(claims);
        assertThat(video.get("room")).isEqualTo("plad-meeting-" + live);
        assertThat(video.get("roomJoin")).isEqualTo(true);
        assertThat(video.get("canSubscribe")).isEqualTo(true);
        assertThat(video.get("canPublishData")).isEqualTo(true);
        assertThat(video.get("canPublish")).isEqualTo(true);
        assertThat(video.get("roomAdmin")).isEqualTo(true);
        assertThat(((List<?>) video.get("canPublishSources")).stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrder("camera", "microphone", "screen_share", "screen_share_audio");
    }

    @Test
    void aRegisteredDependentEntersWithTheirNicknameAndCannotPublishAudioOrVideo() throws Exception {
        String body = join(live, ana).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Claims claims = decode(JsonPath.read(body, "$.token"));

        assertThat(claims.getSubject()).isEqualTo(meetingIdentity.of(live, ana.getId()));
        assertThat(claims.get("name")).isEqualTo(fixtures.nickname(ana));
        assertThat(claims.get("metadata")).isEqualTo("{\"role\":\"dependente\"}");
        Map<?, ?> video = video(claims);
        assertThat(video.get("room")).isEqualTo("plad-meeting-" + live);
        assertThat(video.get("roomJoin")).isEqualTo(true);
        assertThat(video.get("canSubscribe")).isEqualTo(true);
        assertThat(video.get("canPublishData")).isEqualTo(true);          // the chat
        assertThat(video.get("canUpdateOwnMetadata")).isEqualTo(true);    // the raised hand
        // no audio, no video, no screen, and no power over the room
        assertThat(video.get("canPublish")).isEqualTo(false);
        assertThat(video.get("canPublishSources")).isNull();
        assertThat(video.get("roomAdmin")).isNull();
        assertThat(video.get("roomCreate")).isNull();
    }

    @Test
    void noTokenCarriesARealNameAnEmailOrAUserId() throws Exception {
        for (User caller : new User[]{host, ana, bruno}) {
            String token = JsonPath.read(join(live, caller).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString(), "$.token");
            String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

            for (User person : new User[]{ana, bruno, outsider, otherPsychologist}) {
                assertThat(payload).doesNotContain(person.getRealName()).doesNotContain(person.getEmail());
            }
            assertThat(payload).doesNotContain(caller.getEmail());
            // the host's own name is allowed (calendar); a dependent's is not
            if (caller != host) {
                assertThat(payload).doesNotContain(caller.getRealName());
            }
            // no claim value is the user id, and the identity does not contain it
            Claims claims = decode(token);
            assertThat(claims.getSubject()).isNotEqualTo(String.valueOf(caller.getId()));
            allValues(claims).forEach(value -> assertThat(value).isNotEqualTo(String.valueOf(caller.getId())));
            assertThat(claims).doesNotContainKeys("userId", "user_id", "email", "real_name");
        }
    }

    @Test
    void theIdentityIsStableForTheSamePersonAndDiffersAcrossPeopleAndMeetings() throws Exception {
        // another host: one psychologist cannot have two scheduled meetings that cross (meetings_no_overlap)
        long other = fixtures.meeting(otherPsychologist, Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(55)), "scheduled");
        fixtures.register(other, ana);

        String first = identityOf(join(live, ana));
        String again = identityOf(join(live, ana));
        String otherPerson = identityOf(join(live, bruno));
        String otherMeeting = identityOf(join(other, ana));

        assertThat(first).isEqualTo(again);                 // reconnecting keeps the identity
        assertThat(first).isNotEqualTo(otherPerson);
        assertThat(first).isNotEqualTo(otherMeeting);       // the provider cannot follow someone across meetings
        assertThat(first).hasSizeGreaterThanOrEqualTo(32).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void theTokenIsValidUntil15MinutesAfterTheMeetingEnds() throws Exception {
        Instant end = jdbcTemplate.queryForObject("select ends_at from meetings where id = ?", java.sql.Timestamp.class, live)
                .toInstant();

        Instant expiry = decode(JsonPath.read(join(live, ana).andReturn().getResponse().getContentAsString(), "$.token"))
                .getExpiration().toInstant();

        assertThat(expiry).isBetween(end.plus(Duration.ofMinutes(15)).minusSeconds(5),
                end.plus(Duration.ofMinutes(15)).plusSeconds(5));
    }

    // ---- when -----------------------------------------------------------------------------------------------------

    @Test
    void thereIsADoorFromTenMinutesBeforeTheStartUntilTheEnd() throws Exception {
        Instant now = Instant.now();
        // each one has its own host: a psychologist cannot have two scheduled meetings that cross (meetings_no_overlap)
        Slot tooEarly = slot(now.plus(Duration.ofMinutes(12)), now.plus(Duration.ofMinutes(12)).plus(HOUR));
        Slot justOpen = slot(now.plus(Duration.ofMinutes(8)), now.plus(Duration.ofMinutes(8)).plus(HOUR));
        Slot lastMinute = slot(now.minus(Duration.ofMinutes(59)), now.plus(Duration.ofMinutes(1)));
        Slot over = slot(now.minus(HOUR).minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));

        join(tooEarly.id(), ana).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
        join(tooEarly.id(), tooEarly.host()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
        join(justOpen.id(), ana).andExpect(status().isOk());
        join(justOpen.id(), justOpen.host()).andExpect(status().isOk());
        join(lastMinute.id(), ana).andExpect(status().isOk());
        join(over.id(), ana).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
        join(over.id(), over.host()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
    }

    private record Slot(long id, User host) {
    }

    // a scheduled meeting with a host of its own, and ana registered in it
    private Slot slot(Instant start, Instant end) {
        User slotHost = fixtures.psychologist("Anfitria " + start.toEpochMilli());
        long id = fixtures.meeting(slotHost, start, end, "scheduled");
        fixtures.register(id, ana);
        return new Slot(id, slotHost);
    }

    @Test
    void aCancelledOrEndedMeetingIsNotOpenEvenInsideItsHours() throws Exception {
        Instant now = Instant.now();
        long cancelled = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "cancelled");
        long ended = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "ended");
        fixtures.register(cancelled, ana);
        fixtures.register(ended, ana);

        for (long id : new long[]{cancelled, ended}) {
            join(id, ana).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
            join(id, host).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
        }
    }

    // ---- who not --------------------------------------------------------------------------------------------------

    @Test
    void everyoneElseIsForbiddenAndGetsNoToken() throws Exception {
        for (User caller : new User[]{outsider, otherPsychologist, admin}) {
            join(live, caller).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(content().string(not(containsString("plad-meeting"))));
        }
    }

    @Test
    void aStrangerLearnsNothingAboutTheStateOfTheMeeting() throws Exception {
        Instant now = Instant.now();
        long future = fixtures.meeting(host, now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(2)).plus(HOUR), "scheduled");
        long cancelled = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "cancelled");

        for (long id : new long[]{future, cancelled}) {
            join(id, outsider).andExpect(status().isForbidden()); // 403, not 409
            join(id, otherPsychologist).andExpect(status().isForbidden());
        }
    }

    @Test
    void aDependentWhoLeftTheMeetingCannotEnter() throws Exception {
        jdbcTemplate.update("delete from meeting_registrations where meeting_id = ? and dependent_id = ?", live, ana.getId());

        join(live, ana).andExpect(status().isForbidden());
        join(live, bruno).andExpect(status().isOk());
    }

    @Test
    void withoutATokenTheAnswerIs401AndAnUnknownMeetingIs404() throws Exception {
        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + live + "/join")).andExpect(status().isUnauthorized());
        join(999_999_999L, host).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void enteringNeverReachesTheVideoProvider() throws Exception {
        assertThat(gateway).isInstanceOf(FakeMeetingRoomGateway.class);
        FakeMeetingRoomGateway fake = (FakeMeetingRoomGateway) gateway;
        fake.reset();

        join(live, host).andExpect(status().isOk());
        join(live, ana).andExpect(status().isOk());

        assertThat(fake.calls()).isEmpty(); // signing a token is local; no call goes out
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private ResultActions join(long meetingId, User caller) throws Exception {
        return mockMvc.perform(request(HttpMethod.POST, "/meetings/" + meetingId + "/join")
                .header("Authorization", fixtures.bearer(caller)));
    }

    private String identityOf(ResultActions joined) throws Exception {
        return decode(JsonPath.read(joined.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.token")).getSubject();
    }

    private Claims decode(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload();
    }

    private static Map<?, ?> video(Claims claims) {
        return claims.get("video", Map.class);
    }

    // every scalar in the payload, as text
    private static List<String> allValues(Map<?, ?> map) {
        List<String> values = new java.util.ArrayList<>();
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                values.addAll(allValues(nested));
            } else if (value instanceof List<?> list) {
                list.forEach(item -> values.add(String.valueOf(item)));
            } else {
                values.add(String.valueOf(value));
            }
        }
        return values;
    }
}
