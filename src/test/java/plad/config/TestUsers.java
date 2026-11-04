package plad.config;

import org.springframework.jdbc.core.JdbcTemplate;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.UserRepository;

import java.time.LocalDate;
import java.util.UUID;

// JwtAuthenticationFilter compares the token_version claim with the database, so a token is only accepted for a
// user that exists; tests that need a valid bearer token for a given role save a real (committed) user with this
public final class TestUsers {

    private TestUsers() {
    }

    // For tests that need a token for a fixed id (audit entries are keyed by ids the test picked): inserts the row
    // with that id when it is not there yet. Joins the test transaction, so a @Transactional test rolls it back.
    public static void ensureExists(JdbcTemplate jdbc, long id, UserType type) {
        jdbc.update("""
                INSERT INTO users (id, real_name, password_hash, email, birth_date, type_user, gender, created_at)
                VALUES (?, 'test', 'not-a-real-hash', ?, DATE '2000-01-01', ?::user_type, 'outro'::sex, now())
                ON CONFLICT (id) DO NOTHING""", id, "test-fixed-" + id + "@example.com", type.name());
    }

    public static User save(UserRepository users, UserType type) {
        User user = new User();
        user.setRealName("test " + type);
        user.setEmail("test-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-a-real-hash");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(type);
        return users.save(user);
    }
}
