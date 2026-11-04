package plad.services;

import org.junit.jupiter.api.Test;
import plad.exception.TooManyRequestException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterServiceTest {

    private final RateLimiterService rateLimiterService = new RateLimiterService();

    @Test
    void allowsFiveRequestsThenRejectsWithRetryAfter() {
        consumeFiveTimes("client-a");

        // 5 tokens per minute refill one every 12 seconds
        assertThatThrownBy(() -> rateLimiterService.consume("client-a"))
                .isInstanceOfSatisfying(TooManyRequestException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 12L));
    }

    @Test
    void keysHaveIndependentBuckets() {
        consumeFiveTimes("client-a");

        assertThatCode(() -> rateLimiterService.consume("client-b")).doesNotThrowAnyException();
    }

    @Test
    void evictsOnlyBucketsIdleForAWholeRefillPeriod() {
        rateLimiterService.consume("client-a");

        rateLimiterService.evictIdleBuckets(System.nanoTime());
        assertThat(rateLimiterService.trackedKeys()).isEqualTo(1);

        rateLimiterService.evictIdleBuckets(System.nanoTime() + Duration.ofMinutes(1).toNanos());
        assertThat(rateLimiterService.trackedKeys()).isZero();
    }

    @Test
    void loginEmailKeysAllowTenThenReject() {
        String emailKey = RateLimiterService.LOGIN_EMAIL_PREFIX + "a@example.com";
        for (int i = 0; i < 10; i++) {
            rateLimiterService.consume(emailKey);
        }

        // 10 tokens per 15 minutes refill one every 90 seconds
        assertThatThrownBy(() -> rateLimiterService.consume(emailKey))
                .isInstanceOfSatisfying(TooManyRequestException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 90L));

        consumeFiveTimes("client-a");
        assertThatThrownBy(() -> rateLimiterService.consume("client-a"))
                .isInstanceOf(TooManyRequestException.class);
    }

    // 5 a day, as a fixed window: the whole allowance comes back after 24h, not one token every 4.8h
    @Test
    void nicknameRerollKeysAllowFiveADayThenRejectUntilTheNextDay() {
        String key = RateLimiterService.NICKNAME_REROLL_PREFIX + 42;
        for (int i = 0; i < 5; i++) {
            rateLimiterService.consume(key);
        }

        assertThatThrownBy(() -> rateLimiterService.consume(key))
                .isInstanceOfSatisfying(TooManyRequestException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isGreaterThan(23L * 3600).isLessThanOrEqualTo(24L * 3600));
        // another dependent has an allowance of their own
        rateLimiterService.consume(RateLimiterService.NICKNAME_REROLL_PREFIX + 43);
    }

    @Test
    void evictsEmailBucketsOnlyAfterTheirOwnRefillPeriod() {
        rateLimiterService.consume("client-a");
        rateLimiterService.consume(RateLimiterService.LOGIN_EMAIL_PREFIX + "a@example.com");
        long now = System.nanoTime();

        rateLimiterService.evictIdleBuckets(now + Duration.ofMinutes(1).toNanos());
        assertThat(rateLimiterService.trackedKeys()).isEqualTo(1);

        rateLimiterService.evictIdleBuckets(now + Duration.ofMinutes(14).toNanos());
        assertThat(rateLimiterService.trackedKeys()).isEqualTo(1);

        rateLimiterService.evictIdleBuckets(now + Duration.ofMinutes(15).toNanos());
        assertThat(rateLimiterService.trackedKeys()).isZero();
    }

    @Test
    void refundGivesBackOneTokenButNeverBeyondCapacity() {
        consumeFiveTimes("client-a");
        rateLimiterService.refund("client-a");

        assertThatCode(() -> rateLimiterService.consume("client-a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> rateLimiterService.consume("client-a"))
                .isInstanceOf(TooManyRequestException.class);

        // refunding a full bucket must not stack extra tokens
        rateLimiterService.consume("client-b");
        for (int i = 0; i < 3; i++) {
            rateLimiterService.refund("client-b");
        }
        consumeFiveTimes("client-b");
        assertThatThrownBy(() -> rateLimiterService.consume("client-b"))
                .isInstanceOf(TooManyRequestException.class);
    }

    @Test
    void refundOfUnknownKeyCreatesNoBucket() {
        rateLimiterService.refund("client-a");

        assertThat(rateLimiterService.trackedKeys()).isZero();
    }

    private void consumeFiveTimes(String key) {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.consume(key);
        }
    }
}
