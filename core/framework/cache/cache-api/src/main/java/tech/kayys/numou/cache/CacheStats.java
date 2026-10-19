package tech.kayys.numou.cache;

/** Point-in-time counters, exported as metrics by the observability adapter. */
public record CacheStats(long hits, long misses, long loads, long loadFailures, long invalidations) {

    public double hitRatio() {
        final long total = hits + misses;
        return total == 0 ? 0.0d : (double) hits / total;
    }
}
