package plad.controllers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// The seat count holds when requests arrive at the same time (decision 24). Deliberately NOT @Transactional: the
// requests run on other threads, which would not see rows kept inside a test transaction, and the lock on the meeting
// row is only a lock if each request commits on its own. Users are committed and removed in @AfterEach: the
// cascades take their dependents, meetings and registrations with them. Registration writes no audit entry.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class MeetingRegistrationConcurrencyTest {

    private static final int ROUNDS = 10;

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

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void removeWhatTheTestCommitted() {
        for (Long id : userIds) {
            jdbcTemplate.update("delete from users where id = ?", id); // dependents, psychologists, meetings, registrations cascade
        }
    }

    private record Answer(int status, String body) {
    }

    // one seat left, two dependents ask for it at the same instant: one gets in, the other is told it is full
    @Test
    void theLastSeatGoesToExactlyOneOfTwoSimultaneousRequests() throws Exception {
        User host = newPsychologist();
        User holder = newDependent();
        User contenderA = newDependent();
        User contenderB = newDependent();
        Instant day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        for (int round = 0; round < ROUNDS; round++) {
            long meeting = seedMeeting(host, day.plus(2L * round, ChronoUnit.HOURS), 2);
            jdbcTemplate.update("insert into meeting_registrations (meeting_id, dependent_id) values (?, ?)",
                    meeting, holder.getId()); // one of the two seats is already taken

            List<Answer> answers = fireTogether(meeting, List.of(contenderA, contenderB));

            assertThat(answers).as("round %d", round).extracting(Answer::status)
                    .containsExactlyInAnyOrder(200, 409);
            Answer refused = answers.stream().filter(a -> a.status() == 409).findFirst().orElseThrow();
            assertThat((String) JsonPath.read(refused.body(), "$.code")).isEqualTo("MEETING_FULL");
            assertThat(rowsOf(meeting)).as("round %d: never above the capacity", round).isEqualTo(2);
        }
    }

    // several more requests than seats: exactly as many get in as there are seats
    @Test
    void manySimultaneousRequestsFillTheCapacityAndNoMore() throws Exception {
        User host = newPsychologist();
        int capacity = 3;
        List<User> contenders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            contenders.add(newDependent());
        }
        Instant day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        for (int round = 0; round < ROUNDS; round++) {
            long meeting = seedMeeting(host, day.plus(2L * round, ChronoUnit.HOURS), capacity);

            List<Answer> answers = fireTogether(meeting, contenders);

            assertThat(answers.stream().filter(a -> a.status() == 200).count()).as("round %d: got in", round)
                    .isEqualTo(capacity);
            assertThat(answers.stream().filter(a -> a.status() == 409).count()).as("round %d: told it is full", round)
                    .isEqualTo(contenders.size() - capacity);
            assertThat(rowsOf(meeting)).as("round %d: rows", round).isEqualTo(capacity);
        }
    }

    // the same dependent double-clicking: both requests answer 200 and there is one row
    @Test
    void theSameDependentRequestingTwiceAtOnceLeavesOneRow() throws Exception {
        User host = newPsychologist();
        User dependent = newDependent();
        Instant day = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        long meeting = seedMeeting(host, day, 5);

        List<Answer> answers = fireTogether(meeting, List.of(dependent, dependent));

        assertThat(answers).extracting(Answer::status).containsExactly(200, 200);
        assertThat(rowsOf(meeting)).isEqualTo(1);
    }

    // every request waits at the latch and is released at once, so they really overlap
    private List<Answer> fireTogether(long meeting, List<User> callers) throws Exception {
        List<String> tokens = callers.stream().map(jwtService::generateToken).toList(); // before the threads start
        ExecutorService pool = Executors.newFixedThreadPool(callers.size());
        try {
            CountDownLatch ready = new CountDownLatch(callers.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Answer>> futures = new ArrayList<>();
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    var response = mockMvc.perform(post("/meetings/" + meeting + "/registration")
                            .header("Authorization", "Bearer " + token)).andReturn().getResponse();
                    return new Answer(response.getStatus(), response.getContentAsString());
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("all threads are waiting at the start").isTrue();
            go.countDown();
            List<Answer> answers = new ArrayList<>();
            for (Future<Answer> future : futures) {
                answers.add(future.get(30, TimeUnit.SECONDS));
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }

    private int rowsOf(long meeting) {
        return jdbcTemplate.queryForObject("select count(*) from meeting_registrations where meeting_id = ?",
                Integer.class, meeting);
    }

    private long seedMeeting(User host, Instant start, int capacity) {
        return jdbcTemplate.queryForObject("""
                        insert into meetings (psychologist_id, title, starts_at, ends_at, capacity, status)
                        values (?, 'Disputa', ?, ?, ?, 'scheduled') returning id""",
                Long.class, host.getId(), OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(start.plus(1, ChronoUnit.HOURS), ZoneOffset.UTC), capacity);
    }

    private User newPsychologist() {
        User user = newUser(UserType.psicologo);
        Psychologist row = new Psychologist();
        row.setUser(user);
        row.setCredentialType(CrmCrpType.CRP);
        row.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 20));
        psychologistRepository.save(row);
        return user;
    }

    private User newDependent() {
        User user = newUser(UserType.dependente);
        Dependent row = new Dependent();
        row.setNickname(plad.TestNicknames.unique());
        row.setUser(user);
        dependentRepository.save(row);
        return user;
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName("Teste de concorrencia");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        User saved = userRepository.save(user);
        userIds.add(saved.getId());
        return saved;
    }
}
