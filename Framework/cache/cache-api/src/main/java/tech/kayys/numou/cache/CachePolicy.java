package tech.kayys.syirkah.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-cache behaviour.
 *
 * @param ttl         how long a loaded value stays fresh
 * @param negativeTtl how long an absent (null) result stays cached; ZERO disables negative caching
 * @param maxEntries  hard upper bound on entries (eviction beyond this)
 */
public record CachePolicy(Duration ttl, Duration negativeTtl, long maxEntries) {

    public CachePolicy {
        Objects.requireNonNull(ttl, "ttl cannot be null");
        Objects.requireNonNull(negativeTtl, "negativeTtl cannot be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (negativeTtl.isNegative()) {
            throw new IllegalArgumentException("negativeTtl cannot be negative");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
    }

    /** Sensible default for read-mostly reference data. */
    public static CachePolicy referenceData() {
        return new CachePolicy(Duration.ofMinutes(5), Duration.ofSeconds(15), 10_000);
    }

    /** Short-lived default for hot operational reads (e.g. availability). */
    public static CachePolicy shortLived() {
        return new CachePolicy(Duration.ofSeconds(10), Duration.ZERO, 5_000);
    }
}
