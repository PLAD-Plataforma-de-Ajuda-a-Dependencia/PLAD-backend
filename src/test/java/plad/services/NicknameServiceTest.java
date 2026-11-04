package plad.services;

import db.migration.V19__backfill_dependent_nicknames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.AuditAction;
import plad.entity.AuditLog;
import plad.entity.Dependent;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.exception.TooManyRequestException;
import plad.repositories.AuditLogRepository;
import plad.repositories.DependentRepository;
import plad.repositories.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Collision handling and the reroll, against the real Postgres. The service is built by hand with a scripted
// generator (a different @Bean would mean a new Spring context and a new connection pool).
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class NicknameServiceTest {

    @Autowired
    private DependentRepository dependentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aTakenNicknameForcesANewDraw() {
        String taken = unique("Morcego");
        String free = unique("Coruja");
        existingDependent(taken);
        User user = newUser(UserType.dependente);

        String nickname = service(new ScriptedNicknameGenerator().plain(taken, free)).assignInitial(user.getId());

        assertThat(nickname).isEqualTo(free);
        assertThat(storedNickname(user)).isEqualTo(free);
        assertThat(openHistory(user)).containsExactly(free);
    }

    @Test
    void afterTenCollisionsInARowItAppendsANumberAndRedrawsWhenThatCollidesToo() {
        String taken = unique("Lince");
        existingDependent(taken);
        existingDependent(taken + " 7");
        String[] collisions = new String[10];
        java.util.Arrays.fill(collisions, taken);
        User user = newUser(UserType.dependente);

        String nickname = service(new ScriptedNicknameGenerator()
                .plain(collisions)
                .suffixed(taken + " 7", taken + " 8"))
                .assignInitial(user.getId());

        assertThat(nickname).isEqualTo(taken + " 8");
        assertThat(storedNickname(user)).isEqualTo(taken + " 8");
    }

    @Test
    void rerollSwapsTheNicknameKeepsTheHistoryAndAuditsWithoutTheNickname() {
        User user = newUser(UserType.dependente);
        String first = unique("Tatu");
        String second = unique("Arara");
        NicknameService service = service(new ScriptedNicknameGenerator().plain(first, second));
        service.assignInitial(user.getId());

        String rerolled = service.reroll(user.getId());

        assertThat(rerolled).isEqualTo(second);
        assertThat(storedNickname(user)).isEqualTo(second);
        assertThat(openHistory(user)).containsExactly(second);
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT nickname, replaced_at FROM dependent_nickname_history WHERE dependent_id = ? ORDER BY id", user.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("nickname", first);
        assertThat(history.get(0).get("replaced_at")).isNotNull();
        assertThat(history.get(1)).containsEntry("nickname", second);
        assertThat(history.get(1).get("replaced_at")).isNull();

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(e -> e.getAction() == AuditAction.NICKNAME_REROLLED && user.getId().equals(e.getSubjectUserId()))
                .toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getActorId()).isEqualTo(user.getId());
        assertThat(entries.get(0).getEntity()).isEqualTo("DEPENDENT");
        assertThat(entries.get(0).getDetails()).isEqualTo(Map.of("actor_role", "dependente"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT details::text FROM audit_log WHERE id = ?", String.class, entries.get(0).getId()))
                .doesNotContain(first).doesNotContain(second);
    }

    @Test
    void rerollNeverKeepsTheCurrentNicknameNorTakesAnotherDependentsOne() {
        User user = newUser(UserType.dependente);
        String own = unique("Quati");
        String other = unique("Gaivota");
        String free = unique("Ema");
        existingDependent(other);
        NicknameService service = service(new ScriptedNicknameGenerator().plain(own, own, other, free));
        service.assignInitial(user.getId());

        assertThat(service.reroll(user.getId())).isEqualTo(free);
    }

    @Test
    void sixthRerollOfTheDayIsRateLimitedWithRetryAfter() {
        User user = newUser(UserType.dependente);
        ScriptedNicknameGenerator generator = new ScriptedNicknameGenerator();
        String[] draws = new String[7];
        for (int i = 0; i < draws.length; i++) {
            draws[i] = unique("Castor");
        }
        NicknameService service = service(generator.plain(draws));
        service.assignInitial(user.getId());

        for (int i = 0; i < 5; i++) {
            service.reroll(user.getId());
        }

        assertThatThrownBy(() -> service.reroll(user.getId()))
                .isInstanceOfSatisfying(TooManyRequestException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 24L * 3600));
    }

    // V19: dependents that already existed when the feature shipped get a nickname and an open history row
    @Test
    void backfillGivesEveryExistingDependentAUniqueNicknameAndAHistoryRow() {
        jdbcTemplate.execute("ALTER TABLE dependents ALTER COLUMN nickname DROP NOT NULL"); // rolled back with the test
        User a = newUser(UserType.dependente);
        User b = newUser(UserType.dependente);
        User c = newUser(UserType.dependente);
        for (User u : List.of(a, b, c)) {
            jdbcTemplate.update("INSERT INTO dependents (user_id) VALUES (?)", u.getId());
        }
        String same = unique("Texugo");
        String other = unique("Lontra");
        // the second draw collides with the first one, so the migration must draw again
        ScriptedNicknameGenerator generator = new ScriptedNicknameGenerator().plain(same, same, other, unique("Jabuti"));

        Integer filled = jdbcTemplate.execute((ConnectionCallback<Integer>) connection ->
                V19__backfill_dependent_nicknames.backfill(connection, generator));

        assertThat(filled).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dependents WHERE nickname IS NULL", Long.class)).isZero();
        List<String> nicknames = List.of(storedNickname(a), storedNickname(b), storedNickname(c));
        assertThat(nicknames).doesNotHaveDuplicates().contains(same, other);
        for (User u : List.of(a, b, c)) {
            assertThat(openHistory(u)).containsExactly(storedNickname(u));
        }
    }

    // helpers

    private NicknameService service(ScriptedNicknameGenerator generator) {
        return new NicknameService(dependentRepository, generator, new RateLimiterService(), auditService);
    }

    private static String unique(String animal) {
        return animal + " Teste " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String storedNickname(User user) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM dependents WHERE user_id = ?", String.class, user.getId());
    }

    private List<String> openHistory(User user) {
        return jdbcTemplate.queryForList(
                "SELECT nickname FROM dependent_nickname_history WHERE dependent_id = ? AND replaced_at IS NULL",
                String.class, user.getId());
    }

    private void existingDependent(String nickname) {
        User user = newUser(UserType.dependente);
        Dependent dependent = new Dependent();
        dependent.setUser(user);
        dependent.setNickname(nickname);
        dependentRepository.saveAndFlush(dependent);
    }

    private User newUser(UserType type) {
        User user = new User();
        user.setRealName("Teste Nickname");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return userRepository.saveAndFlush(user);
    }
}
