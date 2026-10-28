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

    private void consumeFiveTimes(String key) {
        for (int i = 0; i < 5; i++) {
            rateLimiterService.consume(key);
        }
    }
}
