package plad.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// Daily at 03:00 Brazil time. Revoked tokens are kept for a while on purpose: presenting one again is the reuse
// signal, so deleting them right away would blind that check. Deleting is idempotent, so running on several
// instances at once only wastes a query.
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenService refreshTokens;
    private final Clock clock;
    private final Duration retention;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokens, Clock clock,
                                  @Value("${auth.refresh-token.cleanup-retention-days}") long retentionDays) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${auth.refresh-token.cleanup-cron}", zone = "America/Sao_Paulo")
    public void run() {
        cleanup();
    }

    // deletes tokens that expired, or were revoked, more than the retention period ago; returns how many
    public int cleanup() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = refreshTokens.deleteStale(cutoff);
        log.info("Refresh token cleanup: deleted {} token(s) expired or revoked before {}", deleted, cutoff);
        return deleted;
    }
}
