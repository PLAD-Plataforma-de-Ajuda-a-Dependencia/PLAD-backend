package plad.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import plad.entity.User;
import plad.entity.UserType;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-0123456789";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void tokenCarriesUserIdAndNoEmail() {
        String token = jwtService.generateToken(user(42L));

        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        Claims claims = Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.values()).noneMatch(value -> value.toString().contains("@"));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = new JwtService(SECRET, -1_000).generateToken(user(1L));

        assertThatThrownBy(() -> jwtService.extractUserId(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = new JwtService("another-secret-0123456789-0123456789-0123456789", 60_000).generateToken(user(1L));

        assertThatThrownBy(() -> jwtService.extractUserId(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String token = Jwts.builder().claim("userId", 1L).compact();

        assertThatThrownBy(() -> jwtService.extractUserId(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedTokenIsRejected() {
        assertThatThrownBy(() -> jwtService.extractUserId("not-a-jwt")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonNumericUserIdIsRejected() {
        String token = Jwts.builder().claim("userId", "abc").signWith(KEY).compact();

        assertThatThrownBy(() -> jwtService.extractUserId(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenWithoutUserIdHasNoUser() {
        String token = Jwts.builder().subject("1").signWith(KEY).compact();

        assertThat(jwtService.extractUserId(token)).isNull();
    }

    @Test
    void tokenCarriesTheRoleOfTheUserType() {
        for (UserType type : UserType.values()) {
            String token = jwtService.generateToken(user(1L, type));

            assertThat(jwtService.parse(token).role()).isEqualTo(type);
            Claims claims = Jwts.parser().verifyWith(KEY).build().parseSignedClaims(token).getPayload();
            assertThat(claims.get("role", String.class)).isEqualTo(type.name());
        }
    }

    @Test
    void tokenWithoutRoleHasNoRole() {
        String token = Jwts.builder().claim("userId", 1L).signWith(KEY).compact();

        assertThat(jwtService.parse(token).userId()).isEqualTo(1L);
        assertThat(jwtService.parse(token).role()).isNull();
    }

    @Test
    void unknownRoleIsRejected() {
        String token = Jwts.builder().claim("userId", 1L).claim("role", "superuser").signWith(KEY).compact();

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userWithoutTypeCannotGetAToken() {
        User user = new User();
        user.setId(1L);

        assertThatThrownBy(() -> jwtService.generateToken(user)).isInstanceOf(NullPointerException.class);
    }

    private static User user(Long id) {
        return user(id, UserType.dependente);
    }

    private static User user(Long id, UserType type) {
        User user = new User();
        user.setId(id);
        user.setTypeUser(type);
        user.setEmail("someone@example.com");
        return user;
    }
}
