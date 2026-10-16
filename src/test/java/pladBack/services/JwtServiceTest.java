package pladBack.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import pladBack.entity.User;

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

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("someone@example.com");
        return user;
    }
}
