package plad.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plad.entity.RefreshToken;
import plad.entity.User;
import plad.exception.InvalidRefreshTokenException;
import plad.repositories.RefreshTokenRepository;
import plad.repositories.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    // the access token goes in the response body, the refresh token only in the cookie
    public record Session(String accessToken, String refreshToken, Duration refreshTtl) {}

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final JwtService jwtService;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokens, UserRepository users, JwtService jwtService,
                               Clock clock, @Value("${auth.refresh-token.ttl-days}") long ttlDays) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.jwtService = jwtService;
        this.clock = clock;
        this.ttl = Duration.ofDays(ttlDays);
    }

    // login: a new access token and a new refresh token
    @Transactional
    public Session start(User user) {
        return issue(user);
    }

    // Rotation: the token presented is revoked and a new pair is issued. The failures commit what they changed
    // (noRollbackFor): the mass revocation after a reuse must survive the 401 that reports it.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (!token.getExpiresAt().isAfter(now)) {
            // expired, used or not: nothing to steal any more, so no mass revocation
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        // a token already used (or logged out) is back inside its lifetime: someone holds a copy. The revoke below is
        // conditional on revoked_at being null, so of two concurrent uses only one rotates and the other lands here too.
        if (token.getRevokedAt() != null || refreshTokens.revokeIfActive(token.getId(), now) == 0) {
            int revoked = refreshTokens.revokeAllActiveByUser(token.getUserId(), now);
            log.warn("Refresh token reuse for user {}: revoked {} active refresh token(s)", token.getUserId(), revoked);
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        return issue(user);
    }

    // logout: unknown, expired or already revoked tokens are simply ignored
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokens.revokeActiveByHash(hash(rawToken), clock.instant());
        }
    }

    @Transactional
    public int revokeAll(Long userId) {
        return refreshTokens.revokeAllActiveByUser(userId, clock.instant());
    }

    // for RefreshTokenCleanupJob: expired or revoked before the cutoff
    @Transactional
    public int deleteStale(Instant cutoff) {
        return refreshTokens.deleteStale(cutoff);
    }

    private Session issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), hash(raw), clock.instant().plus(ttl)));
        return new Session(jwtService.generateToken(user), raw, ttl);
    }

    // lowercase hex of the SHA-256 of the token: what refresh_tokens.token_hash holds
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
