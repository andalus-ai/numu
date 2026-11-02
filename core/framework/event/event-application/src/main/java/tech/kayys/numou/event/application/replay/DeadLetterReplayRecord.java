package tech.kayys.numou.event.application.replay;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable source record captured from a broker DLQ. Raw payload is never exposed by an HTTP API. */
public record DeadLetterReplayRecord(
        UUID tenantId,
        DeadLetterSourceKey source,
        String eventType,
        String payload,
        String failureReason,
        Instant failedAt) {
    public DeadLetterReplayRecord {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(source, "source must not be null");
        eventType = required(eventType, "eventType", 200);
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        failureReason = required(failureReason, "failureReason", 1000);
        Objects.requireNonNull(failedAt, "failedAt must not be null");
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
