package plad.services;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import plad.exception.TooManyRequestException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimiterService {

    public static final String LOGIN_EMAIL_PREFIX = "login-email:";

    private record Limit(int capacity, Duration refillPeriod) {
    }

    private static final Limit DEFAULT_LIMIT = new Limit(5, Duration.ofMinutes(1));
    // keys starting with one of these prefixes get their own limit; per-IP keys start with "/" so they never match
    private static final Map<String, Limit> LIMITS_BY_PREFIX = Map.of(
            LOGIN_EMAIL_PREFIX, new Limit(10, Duration.ofMinutes(15)));
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private record Entry(Bucket bucket, Limit limit, AtomicLong lastSeenNanos) {
    }

    private final ConcurrentMap<String, Entry> buckets = new ConcurrentHashMap<>();

    public void consume(String key) {
        Entry entry = buckets.computeIfAbsent(key, k -> newEntry(limitFor(k)));
        entry.lastSeenNanos().set(System.nanoTime());

        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1, Math.ceilDiv(probe.getNanosToWaitForRefill(), NANOS_PER_SECOND));
            throw new TooManyRequestException("Too many requests. Try again in " + retryAfterSeconds + " seconds.", retryAfterSeconds);
        }
    }

    // gives back a token taken by consume(), never filling the bucket past its capacity
    public void refund(String key) {
        Entry entry = buckets.get(key);
        if (entry != null) {
            entry.lastSeenNanos().set(System.nanoTime());
            entry.bucket().addTokens(1);
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void evictIdleBuckets() {
        evictIdleBuckets(System.nanoTime());
    }

    // a bucket untouched for a whole refill period of its own is full again, so dropping it loses nothing
    void evictIdleBuckets(long nowNanos) {
        buckets.values().removeIf(entry ->
                nowNanos - entry.lastSeenNanos().get() >= entry.limit().refillPeriod().toNanos());
    }

    int trackedKeys() {
        return buckets.size();
    }

    private static Limit limitFor(String key) {
        return LIMITS_BY_PREFIX.entrySet().stream()
                .filter(e -> key.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DEFAULT_LIMIT);
    }

    private static Entry newEntry(Limit limit) {
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(limit.capacity(), limit.refillPeriod()))
                .build();
        return new Entry(bucket, limit, new AtomicLong());
    }
}
