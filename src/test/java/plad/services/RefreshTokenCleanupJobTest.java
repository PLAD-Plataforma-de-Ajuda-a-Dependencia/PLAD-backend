package plad.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import plad.config.TestcontainersConfig;
import plad.entity.RefreshToken;
import plad.entity.Sex;
import plad.entity.User;
import plad.entity.UserType;
import plad.repositories.RefreshTokenRepository;
import plad.repositories.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The job is built by hand with a fixed Clock over the real repository: "7 days ago" is exact, not "about now".
@SpringBootTest
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class RefreshTokenCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-11-04T06:00:00Z"); // 03:00 in Sao Paulo

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private UserRepository users;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void deletesTokensExpiredOrRevokedMoreThanSevenDaysAgoAndKeepsTheRest() {
        Long userId = newUser();
        String expiredLongAgo = save(userId, NOW.minus(Duration.ofDays(8)), null);
        String revokedLongAgo = save(userId, NOW.plus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(8)));
        String expiredRecently = save(userId, NOW.minus(Duration.ofDays(1)), null);
        String revokedRecently = save(userId, NOW.plus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)));
        String active = save(userId, NOW.plus(Duration.ofDays(6)), null);

        int deleted = job(7).cleanup();

        assertThat(deleted).isEqualTo(2);
        assertThat(exists(expiredLongAgo)).isFalse();
        assertThat(exists(revokedLongAgo)).isFalse();
        assertThat(exists(expiredRecently)).isTrue();
        assertThat(exists(revokedRecently)).isTrue();
        assertThat(exists(active)).isTrue();
    }

    @Test
    void tokenRevokedExactlyAtTheRetentionLimitIsKept() {
        Long userId = newUser();
        String atTheLimit = save(userId, NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(7)));
        String justPast = save(userId, NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(7)).minusSeconds(1));

        assertThat(job(7).cleanup()).isEqualTo(1);

        assertThat(exists(atTheLimit)).isTrue();
        assertThat(exists(justPast)).isFalse();
    }

    @Test
    void retentionIsConfigurable() {
        Long userId = newUser();
        String threeDaysOld = save(userId, NOW.plus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(3)));

        assertThat(job(7).cleanup()).isZero();
        assertThat(exists(threeDaysOld)).isTrue();
        assertThat(job(2).cleanup()).isEqualTo(1);
        assertThat(exists(threeDaysOld)).isFalse();
    }

    @Test
    void logsHowManyTokensItDeleted(CapturedOutput output) {
        Long userId = newUser();
        save(userId, NOW.minus(Duration.ofDays(30)), null);
        save(userId, NOW.minus(Duration.ofDays(31)), null);

        job(7).cleanup();

        assertThat(output).contains("Refresh token cleanup: deleted 2 token(s)");
    }

    @Test
    void runsDailyAtThreeInSaoPaulo() throws NoSuchMethodException {
        Scheduled scheduled = RefreshTokenCleanupJob.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("America/Sao_Paulo");
        assertThat(scheduled.cron()).isEqualTo("${auth.refresh-token.cleanup-cron}");
    }

    // helpers

    private RefreshTokenCleanupJob job(long retentionDays) {
        return new RefreshTokenCleanupJob(refreshTokenService, clock, retentionDays);
    }

    private String save(Long userId, Instant expiresAt, Instant revokedAt) {
        String hash = RefreshTokenService.hash(UUID.randomUUID().toString());
        RefreshToken token = new RefreshToken(userId, hash, expiresAt);
        token.setRevokedAt(revokedAt);
        refreshTokens.saveAndFlush(token);
        return hash;
    }

    private boolean exists(String hash) {
        return refreshTokens.findByTokenHash(hash).isPresent();
    }

    private Long newUser() {
        User user = new User();
        user.setRealName("cleanup test");
        user.setEmail("cleanup-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Sex.outro);
        user.setTypeUser(UserType.dependente);
        return users.saveAndFlush(user).getId();
    }
}
