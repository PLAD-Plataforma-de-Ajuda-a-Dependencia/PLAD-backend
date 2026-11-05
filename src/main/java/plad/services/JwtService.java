package plad.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import plad.entity.User;
import plad.entity.UserType;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "userId";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_VERSION_CLAIM = "tv";
    private static final int MIN_SECRET_BYTES = 32;
    // the placeholder .env.example used to ship; long enough to pass the length check, so refused by name
    private static final String OLD_EXAMPLE_SECRET = "troque-por-saida-de-openssl-rand-base64-48";

    // role and tokenVersion are null for tokens issued before the claims existed; the filter treats those as invalid
    public record TokenClaims(Long userId, UserType role, Integer tokenVersion) {}

    private final SecretKey secretKey;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(requireStrongSecret(secret));
        this.parser = Jwts.parser().verifyWith(secretKey).build();
        this.expirationMs = expirationMs;
    }

    // the message is fixed on purpose: it must never carry the configured value
    private static byte[] requireStrongSecret(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secret == null || secret.isBlank() || bytes.length < MIN_SECRET_BYTES || OLD_EXAMPLE_SECRET.equals(secret.strip())) {
            throw new IllegalStateException(
                    "JWT_SECRET ausente ou com menos de 32 bytes; gere com openssl rand -base64 48");
        }
        return bytes;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        // the subject is the user id, not the email: JWT payloads are only base64, so no personal data in them
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(USER_ID_CLAIM, user.getId())
                .claim(ROLE_CLAIM, Objects.requireNonNull(user.getTypeUser(), "user has no type_user").name())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    // null when the token is valid but has no userId claim
    public Long extractUserId(String token) {
        return parse(token).userId();
    }

    // verifies the signature once; a claim that is missing comes back null, a malformed or unknown one is rejected
    public TokenClaims parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            return new TokenClaims(claims.get(USER_ID_CLAIM, Long.class), role == null ? null : UserType.valueOf(role),
                    claims.get(TOKEN_VERSION_CLAIM, Integer.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid or expired token", e);
        }
    }
}
