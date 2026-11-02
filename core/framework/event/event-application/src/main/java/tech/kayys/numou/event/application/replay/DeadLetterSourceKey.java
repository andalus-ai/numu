package tech.kayys.numou.event.application.replay;

import java.util.Objects;

/** Stable locator for one broker dead-letter record; never a business idempotency key. */
public record DeadLetterSourceKey(String clusterId, String topic, int partition, long offset) {
    public DeadLetterSourceKey {
        clusterId = required(clusterId, "clusterId", 200);
        topic = required(topic, "topic", 249);
        if (partition < 0) throw new IllegalArgumentException("partition must not be negative");
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return normalized;
    }
}
