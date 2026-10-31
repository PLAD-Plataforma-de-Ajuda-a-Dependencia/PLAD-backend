package plad.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import plad.config.TestcontainersConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// the trail only accepts INSERT: the guarantee lives in the database, not in the application
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class AuditLogTriggerTest {

    // ids that no users row has: the log has no foreign key, so it outlives account deletion
    private static final long GONE_USER = 987_654_321L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertWorksAndSurvivesAUserThatDoesNotExist() {
        long id = insertEntry();

        Long subject = jdbcTemplate.queryForObject("select subject_user_id from audit_log where id = ?", Long.class, id);
        assertThat(subject).isEqualTo(GONE_USER);
    }

    @Test
    void updateIsRejected() {
        long id = insertEntry();

        assertThatThrownBy(() -> jdbcTemplate.update("update audit_log set action = 'TRIAGE_VIEWED' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void deleteIsRejected() {
        long id = insertEntry();

        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_log where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void truncateIsRejected() {
        insertEntry();

        assertThatThrownBy(() -> jdbcTemplate.execute("truncate table audit_log"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private long insertEntry() {
        return jdbcTemplate.queryForObject(
                "insert into audit_log (actor_id, action, subject_user_id, entity, entity_id, details) "
                        + "values (?, 'TRIAGE_SUBMITTED', ?, 'TRIAGE', 1, '{\"actor_role\": \"dependente\"}'::jsonb) returning id",
                Long.class, GONE_USER, GONE_USER);
    }
}
