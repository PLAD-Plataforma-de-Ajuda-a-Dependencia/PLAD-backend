package plad.services;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import plad.entity.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "userId";

    private final SecretKey secretKey;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(secretKey).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        // the subject is the user id, not the email: JWT payloads are only base64, so no personal data in them
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(USER_ID_CLAIM, user.getId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    // null when the token is valid but has no userId claim
    public Long extractUserId(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload().get(USER_ID_CLAIM, Long.class);
        } catch (JwtException e) {
            throw new IllegalArgumentException("Invalid or expired token", e);
        }
    }
}
