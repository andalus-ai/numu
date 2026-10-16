package tech.kayys.syirkah.security.application.ratelimit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Per-key token bucket. In-process, so with N replicas the effective limit is up
 * to N times the configured one; put a shared limiter (gateway / Redis) in front
 * if you need a hard global cap. Memory is bounded by {@code maxKeys} (LRU).
 */
public final class TokenBucketRateLimiter {

    public record Decision(boolean allowed, long remaining, Duration retryAfter) { }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;
    }

    private final double capacity;
    private final double refillPerNano;
    private final LongSupplier nanoTime;
    private final Map<String, Bucket> buckets;

    /**
     * @param capacity        burst size
     * @param refillPerSecond sustained rate
     * @param maxKeys         tracked keys before the least-recently-used is dropped
     */
    public TokenBucketRateLimiter(long capacity, double refillPerSecond, int maxKeys, LongSupplier nanoTime) {
        if (capacity < 1 || refillPerSecond <= 0 || maxKeys < 1) {
            throw new IllegalArgumentException("capacity, refillPerSecond and maxKeys must be positive");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoTime = nanoTime;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    public TokenBucketRateLimiter(long capacity, double refillPerSecond, int maxKeys) {
        this(capacity, refillPerSecond, maxKeys, System::nanoTime);
    }

    public synchronized Decision tryAcquire(String key) {
        final long now = nanoTime.getAsLong();
        final Bucket b = buckets.computeIfAbsent(key, k -> {
            final var nb = new Bucket();
            nb.tokens = capacity;
            nb.lastRefillNanos = now;
            return nb;
        });
        b.tokens = Math.min(capacity, b.tokens + (now - b.lastRefillNanos) * refillPerNano);
        b.lastRefillNanos = now;
        if (b.tokens >= 1.0d) {
            b.tokens -= 1.0d;
            return new Decision(true, (long) b.tokens, Duration.ZERO);
        }
        final long waitNanos = (long) Math.ceil((1.0d - b.tokens) / refillPerNano);
        return new Decision(false, 0, Duration.ofNanos(waitNanos));
    }
}
