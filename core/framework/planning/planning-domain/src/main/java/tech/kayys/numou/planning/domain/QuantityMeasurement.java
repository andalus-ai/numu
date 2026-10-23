package tech.kayys.numou.planning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable actual-quantity evidence against one exact tenant-owned definition revision. */
public record QuantityMeasurement(
        UUID id,
        UUID tenantId,
        UUID definitionId,
        LocalDate asOfDate,
        BigDecimal actualQuantity,
        Instant recordedAt,
        String recordedBy,
        String idempotencyKey,
        String sourceReference) {

    public QuantityMeasurement {
        Objects.requireNonNull(id, "Measurement id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(actualQuantity, "Actual quantity cannot be null");
        Objects.requireNonNull(recordedAt, "Recorded timestamp cannot be null");
        recordedBy = required(recordedBy, "Recorded by");
        idempotencyKey = required(idempotencyKey, "Idempotency key");
        sourceReference = sourceReference == null || sourceReference.isBlank() ? null : sourceReference.trim();
        if (actualQuantity.signum() < 0) throw new IllegalArgumentException("Actual quantity cannot be negative");
        if (actualQuantity.scale() > 6) throw new IllegalArgumentException("Actual quantity supports at most six decimal places");
        if (recordedBy.length() > 240 || idempotencyKey.length() > 240
                || (sourceReference != null && sourceReference.length() > 500)) {
            throw new IllegalArgumentException("Measurement field exceeds supported length");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
