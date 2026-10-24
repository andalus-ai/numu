package tech.kayys.numou.omnichannel.core;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
/** Bounded exponential backoff with stable per-event jitter. */
public final class ExponentialBackoffRetryPolicy implements InboundRetryPolicy {
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double jitterFraction;
    public ExponentialBackoffRetryPolicy(Duration baseDelay, Duration maxDelay, double jitterFraction) {
        this.baseDelay = positive(baseDelay, "baseDelay");
        this.maxDelay = positive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(baseDelay) < 0) throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        if (!Double.isFinite(jitterFraction) || jitterFraction < 0.0 || jitterFraction > 0.5)
            throw new IllegalArgumentException("jitterFraction must be between 0 and 0.5");
        this.jitterFraction = jitterFraction;
    }
    @Override public Duration delayFor(InboundWorkItem item) {
        Objects.requireNonNull(item, "item must not be null");
        int retryNumber = Math.max(0, item.attemptCount() - 1);
        long baseMillis = Math.max(1L, baseDelay.toMillis());
        long maxMillis = Math.max(1L, maxDelay.toMillis());
        long bounded;
        if (baseMillis >= maxMillis || retryNumber >= 62) bounded = maxMillis;
        else {
            long multiplier = 1L << retryNumber;
            bounded = baseMillis > maxMillis / multiplier ? maxMillis : Math.min(maxMillis, baseMillis * multiplier);
        }
        if (jitterFraction == 0.0 || bounded <= 1) return Duration.ofMillis(bounded);
        double unit = stableUnitInterval(item);
        double factor = 1.0 - jitterFraction + (2.0 * jitterFraction * unit);
        return Duration.ofMillis(Math.max(1L, Math.min(maxMillis, Math.round(bounded * factor))));
    }
    private double stableUnitInterval(InboundWorkItem item) {
        String identity = item.key().tenantId() + "|" + item.key().providerId() + "|"
                + item.key().providerAccountId() + "|" + item.key().externalEventId() + "|" + item.attemptCount();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 7; i++) value = (value << 8) | (digest[i] & 0xffL);
            return value / (double) 0x00ffffffffffffffL;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
    private static Duration positive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative() || value.toMillis() < 1)
            throw new IllegalArgumentException(field + " must be at least one millisecond");
        return value;
    }
}
