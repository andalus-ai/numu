package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, attributable snapshot of actual activity completion at a business date.
 * Observations are history, not mutable fields on the schedule forecast.
 */
public record ActivityProgressObservation(
        UUID id,
        ActivityId activityId,
        LocalDate asOfDate,
        BigDecimal percentComplete,
        Instant recordedAt,
        String recordedBy,
        String idempotencyKey,
        String sourceReference) {

    public ActivityProgressObservation {
        Objects.requireNonNull(id, "Observation id cannot be null");
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(percentComplete, "Percent complete cannot be null");
        Objects.requireNonNull(recordedAt, "Recorded time cannot be null");
        percentComplete = percentComplete.stripTrailingZeros();
        if (percentComplete.compareTo(BigDecimal.ZERO) < 0
                || percentComplete.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percent complete must be between 0 and 100");
        }
        recordedBy = requireText(recordedBy, "Recorded by");
        idempotencyKey = requireText(idempotencyKey, "Idempotency key");
        sourceReference = sourceReference == null || sourceReference.isBlank()
                ? null : sourceReference.trim();
    }

    public static ActivityProgressObservation record(
            ActivityId activityId, LocalDate asOfDate, BigDecimal percentComplete,
            Instant recordedAt, String recordedBy, String idempotencyKey, String sourceReference) {
        return new ActivityProgressObservation(UUID.randomUUID(), activityId, asOfDate,
                percentComplete, recordedAt, recordedBy, idempotencyKey, sourceReference);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
