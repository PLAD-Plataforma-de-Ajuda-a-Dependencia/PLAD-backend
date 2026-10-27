package pladBack.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pladBack.exception.TooManyRequestException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimiterService {

    private static final int CAPACITY = 5;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private record Entry(Bucket bucket, AtomicLong lastSeenNanos) {
    }

    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();

    public void consume(String key) {
        Entry entry = buckets.computeIfAbsent(key, k -> new Entry(createNewBucket(), new AtomicLong()));
        entry.lastSeenNanos().set(System.nanoTime());

        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, Math.ceilDiv(probe.getNanosToWaitForRefill(), NANOS_PER_SECOND));
            throw new TooManyRequestException("Too many requests. Try again in " + retryAfterSeconds + " seconds.", retryAfterSeconds);
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void evictIdleBuckets() {
        evictIdleBuckets(System.nanoTime());
    }

    // a bucket untouched for a whole refill period is full again, so dropping it loses nothing
    void evictIdleBuckets(long nowNanos) {
        long idleLimit = REFILL_PERIOD.toNanos();
        buckets.values().removeIf(entry -> nowNanos - entry.lastSeenNanos().get() >= idleLimit);
    }

    int trackedKeys() {
        return buckets.size();
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.simple(CAPACITY, REFILL_PERIOD);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
