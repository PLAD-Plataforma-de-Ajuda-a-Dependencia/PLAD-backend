package plad.controllers;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.User;
import plad.exception.MeetingParticipantNotFoundException;
import plad.exception.RoomUnavailableException;
import plad.repositories.DependentRepository;
import plad.repositories.PsychologistRepository;
import plad.repositories.UserRepository;
import plad.services.JwtService;
import plad.services.room.FakeMeetingRoomGateway;
import plad.services.room.FakeMeetingRoomGateway.Call;
import plad.services.room.MeetingIdentity;
import plad.services.room.MeetingRoomGateway;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Moderation of the live room (RF006): POST /meetings/{id}/participants/{identity}/speak and /remove, and the room being
// closed when the host cancels or ends the meeting. The video provider is the fake: what matters is who may ask, which
// identities are accepted, what is sent to the provider and what stays in our database when it fails.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class MeetingModerationTest {

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

    private FakeMeetingRoomGateway fake;
    private LiveRoomFixtures fixtures;
    private User host;
    private User otherPsychologist;
    private User ana;      // registered
    private User bruno;    // registered
    private User outsider; // not in the meeting
    private User admin;
    private long live;     // started 5 minutes ago, ends in 55

    @BeforeEach
    void setUp() {
        fake = (FakeMeetingRoomGateway) gateway;
        fake.reset();
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

    // the fake is shared by every context-sharing test: never leave it failing
    @AfterEach
    void cleanUp() {
        fake.reset();
    }

    // ---- speak ----------------------------------------------------------------------------------------------------

    @Test
    void theHostAllowsAndTakesAwayTheSpeechOfARegisteredDependent() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        speak(live, identity, true, host).andExpect(status().isNoContent());
        speak(live, identity, false, host).andExpect(status().isNoContent());

        assertThat(fake.calls()).containsExactly(
                new Call("speak", "plad-meeting-" + live, identity, true),
                new Call("speak", "plad-meeting-" + live, identity, false));
    }

    @Test
    void anIdentityThatIsNotARegisteredDependentOfThisMeetingNeverReachesTheProvider() throws Exception {
        // another host: one psychologist cannot have two scheduled meetings that cross (meetings_no_overlap)
        long another = fixtures.meeting(otherPsychologist, Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(55)), "scheduled");
        fixtures.register(another, outsider);
        String[] strangers = {
                meetingIdentity.of(live, outsider.getId()),     // a dependent who is not in this meeting
                meetingIdentity.of(another, outsider.getId()),  // an identity from another meeting
                meetingIdentity.of(another, ana.getId()),       // ana's identity in the other meeting
                meetingIdentity.of(live, host.getId()),         // the host's own
                "nao-existe", "AAAA"                            // made up
        };

        for (String identity : strangers) {
            speak(live, identity, true, host).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEETING_PARTICIPANT_NOT_FOUND"));
            remove(live, identity, host).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEETING_PARTICIPANT_NOT_FOUND"));
        }

        assertThat(fake.calls()).isEmpty();
        assertThat(fixtures.isRegistered(live, ana)).isTrue();
    }

    @Test
    void onlyTheHostPsychologistModerates() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        for (User caller : new User[]{otherPsychologist, ana, bruno, outsider, admin}) {
            speak(live, identity, true, caller).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            remove(live, identity, caller).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        assertThat(fake.calls()).isEmpty();
        assertThat(fixtures.isRegistered(live, ana)).isTrue();
    }

    @Test
    void withoutATokenTheAnswerIs401AndAnUnknownMeetingIs404() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + live + "/participants/" + identity + "/speak")
                .contentType(MediaType.APPLICATION_JSON).content("{\"allowed\":true}")).andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + live + "/participants/" + identity + "/remove"))
                .andExpect(status().isUnauthorized());
        speak(999_999_999L, identity, true, host).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        remove(999_999_999L, identity, host).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
    }

    @Test
    void speakNeedsTheAllowedFlag() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        mockMvc.perform(request(HttpMethod.POST, "/meetings/" + live + "/participants/" + identity + "/speak")
                        .header("Authorization", fixtures.bearer(host)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void aMeetingThatIsNotScheduledCannotBeModerated() throws Exception {
        Instant now = Instant.now();
        long cancelled = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "cancelled");
        long ended = fixtures.meeting(host, now.minus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(55)), "ended");
        for (long id : new long[]{cancelled, ended}) {
            fixtures.register(id, ana);
            String identity = meetingIdentity.of(id, ana.getId());

            speak(id, identity, true, host).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
            remove(id, identity, host).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEETING_NOT_OPEN"));
            assertThat(fixtures.isRegistered(id, ana)).isTrue();
        }

        assertThat(fake.calls()).isEmpty();
    }

    @Test
    void speakingToSomeoneNotConnectedIs404AndAProviderOutageIs503() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        fake.failWith(new MeetingParticipantNotFoundException("That participant is not in the room"));
        speak(live, identity, true, host).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEETING_PARTICIPANT_NOT_FOUND"));

        fake.failWith(new RoomUnavailableException("The video provider did not answer"));
        speak(live, identity, true, host).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
    }

    // ---- remove ---------------------------------------------------------------------------------------------------

    @Test
    void removingDropsTheDependentFromTheRoomAndFromTheMeetingAndTheyCannotComeBack() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());

        remove(live, identity, host).andExpect(status().isNoContent());

        assertThat(fake.calls()).containsExactly(new Call("remove", "plad-meeting-" + live, identity, null));
        assertThat(fixtures.isRegistered(live, ana)).isFalse();
        assertThat(fixtures.isRegistered(live, bruno)).isTrue(); // only that one
        join(live, ana).andExpect(status().isForbidden());       // a new token is refused
        join(live, bruno).andExpect(status().isOk());
        // the seat is free again, and the identity no longer names anyone registered
        remove(live, identity, host).andExpect(status().isNotFound());
    }

    // LiveKit does not invalidate the token of someone removed, so the only door to a new one must be shut: POST /join
    // is the one place that issues tokens, and it answers 403 once the registration is gone
    @Test
    void aRemovedDependentGetsNoNewTokenFromJoin() throws Exception {
        join(live, ana).andExpect(status().isOk());      // before: they could ask for a token
        join(live, bruno).andExpect(status().isOk());

        remove(live, meetingIdentity.of(live, ana.getId()), host).andExpect(status().isNoContent());

        join(live, ana).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.token").doesNotExist());
        join(live, ana).andExpect(status().isForbidden()); // asking again changes nothing
        join(live, bruno).andExpect(status().isOk());      // only that one
    }

    @Test
    void aRegisteredDependentWhoIsNotConnectedIsStillRemoved() throws Exception {
        fake.failWith(new MeetingParticipantNotFoundException("That participant is not in the room"));

        remove(live, meetingIdentity.of(live, ana.getId()), host).andExpect(status().isNoContent());

        assertThat(fixtures.isRegistered(live, ana)).isFalse();
    }

    @Test
    void ifTheProviderFailsNothingChangesAndTheHostCanTryAgain() throws Exception {
        String identity = meetingIdentity.of(live, ana.getId());
        fake.failWith(new RoomUnavailableException("The video provider did not answer"));

        remove(live, identity, host).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));

        assertThat(fixtures.isRegistered(live, ana)).isTrue();

        fake.reset();
        remove(live, identity, host).andExpect(status().isNoContent());
        assertThat(fixtures.isRegistered(live, ana)).isFalse();
    }

    // ---- the room closes with the meeting ---------------------------------------------------------------------------

    @Test
    void endingTheMeetingClosesTheRoom() throws Exception {
        send("/meetings/" + live + "/end", host).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ended"));

        assertThat(fake.calls()).containsExactly(new Call("close", "plad-meeting-" + live, null, null));
    }

    // Not @Transactional on purpose (like MeetingRegistrationConcurrencyTest): the rollback of the request's own
    // transaction is what is being proved, and inside a test transaction the update would simply stay. The rows it
    // commits are deleted at the end (the foreign keys cascade from users).
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ifTheRoomCannotBeClosedTheMeetingStaysScheduled() throws Exception {
        try {
            fake.failWith(new RoomUnavailableException("The video provider did not answer"));

            send("/meetings/" + live + "/end", host).andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "5"))
                    .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
            send("/meetings/" + live + "/cancel", host).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));

            assertThat(statusOf(live)).isEqualTo("scheduled");
            assertThat(jdbcTemplate.queryForObject("select ended_at is null and cancelled_at is null from meetings where id = ?",
                    Boolean.class, live)).isTrue();

            fake.reset();
            send("/meetings/" + live + "/end", host).andExpect(status().isOk());
            assertThat(statusOf(live)).isEqualTo("ended");
        } finally {
            for (User user : new User[]{host, otherPsychologist, ana, bruno, outsider, admin}) {
                jdbcTemplate.update("delete from users where id = ?", user.getId());
            }
        }
    }

    private String statusOf(long meetingId) {
        return jdbcTemplate.queryForObject("select status from meetings where id = ?", String.class, meetingId);
    }

    @Test
    void cancellingInsideTheJoinWindowClosesTheRoomButEarlierDoesNotCallTheProvider() throws Exception {
        Instant now = Instant.now();
        // soon crosses `live` (same host would break meetings_no_overlap), so it belongs to the other psychologist
        long soon = fixtures.meeting(otherPsychologist, now.plus(Duration.ofMinutes(5)), now.plus(Duration.ofMinutes(5)).plus(HOUR), "scheduled");
        long later = fixtures.meeting(host, now.plus(Duration.ofDays(2)), now.plus(Duration.ofDays(2)).plus(HOUR), "scheduled");

        send("/meetings/" + later + "/cancel", host).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("cancelled"));
        assertThat(fake.calls()).isEmpty(); // nobody can be inside yet: cancelling does not depend on the provider

        fake.failWith(new RoomUnavailableException("The video provider did not answer"));
        send("/meetings/" + later + "/cancel", host).andExpect(status().isConflict()); // already cancelled, still no call
        assertThat(fake.calls()).isEmpty();

        fake.reset();
        send("/meetings/" + soon + "/cancel", otherPsychologist).andExpect(status().isOk());
        assertThat(fake.calls()).containsExactly(new Call("close", "plad-meeting-" + soon, null, null));
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private ResultActions speak(long meetingId, String identity, boolean allowed, User caller) throws Exception {
        return mockMvc.perform(request(HttpMethod.POST, "/meetings/" + meetingId + "/participants/" + identity + "/speak")
                .header("Authorization", fixtures.bearer(caller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allowed\":" + allowed + "}"));
    }

    private ResultActions remove(long meetingId, String identity, User caller) throws Exception {
        return mockMvc.perform(request(HttpMethod.POST, "/meetings/" + meetingId + "/participants/" + identity + "/remove")
                .header("Authorization", fixtures.bearer(caller)));
    }

    private ResultActions join(long meetingId, User caller) throws Exception {
        return send("/meetings/" + meetingId + "/join", caller);
    }

    private ResultActions send(String path, User caller) throws Exception {
        return mockMvc.perform(request(HttpMethod.POST, path).header("Authorization", fixtures.bearer(caller)));
    }
}
