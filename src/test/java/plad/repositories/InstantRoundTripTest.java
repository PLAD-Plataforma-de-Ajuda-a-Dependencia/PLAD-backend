package plad.repositories;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import plad.config.TestcontainersConfig;
import plad.entity.CrmCrpType;
import plad.entity.Psychologist;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class InstantRoundTripTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PsychologistRepository psychologistRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void jvmIsOutsideUtc() {
        // set by the surefire argLine; without it these tests would pass without exercising any zone conversion
        assertThat(ZoneId.systemDefault())
                .as("run through Maven or pass -Duser.timezone=America/Sao_Paulo to the test JVM")
                .isEqualTo(SAO_PAULO);
    }

    @Test
    void instantSavedComesBackIdentical() {
        // 23:30 the previous day in Sao Paulo, so a zone shift would also move the date
        Instant validateTime = Instant.parse("2026-03-15T02:30:00.123456Z");
        Psychologist saved = psychologistRepository.saveAndFlush(newPsychologist(newUser(), validateTime));
        entityManager.clear();

        Psychologist reloaded = psychologistRepository.findById(saved.getUserId()).orElseThrow();

        assertThat(reloaded).isNotSameAs(saved);
        assertThat(reloaded.getValidateTime()).isEqualTo(validateTime);
    }

    @Test
    void createdAtFilledByTheDatabaseDefaultIsTheCurrentInstant() {
        // raw SQL so created_at comes from DEFAULT now() instead of @PrePersist; the session runs in Sao Paulo time,
        // so a TIMESTAMP column would hold local wall-clock time that Hibernate then reads as UTC (3 hours off)
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO users (real_name, email, password_hash, birth_date, gender, type_user) "
                        + "VALUES ('default test', ?, 'test-hash', DATE '1990-01-01', 'outro', 'psicologo') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.com");

        User user = userRepository.findById(id).orElseThrow();

        assertThat(Duration.between(user.getCreatedAt(), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void noApplicationColumnIsTimestampWithoutTimeZone() {
        List<String> naive = jdbcTemplate.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' "
                        + "AND data_type = 'timestamp without time zone' ORDER BY 1",
                String.class);

        assertThat(naive).as("date/time columns must be TIMESTAMPTZ").isEmpty();
    }

    private User newUser() {
        User user = new User();
        user.setRealName("instant test");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.psicologo);
        return userRepository.save(user);
    }

    private Psychologist newPsychologist(User user, Instant validateTime) {
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(user);
        psychologist.setCredentialType(CrmCrpType.CRP);
        psychologist.setCrmCrpNumber(UUID.randomUUID().toString().substring(0, 30));
        psychologist.setValidateTime(validateTime);
        return psychologist;
    }
}
