package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only audit link declaring that a later measurement corrects an earlier one.
 * The original and replacement evidence remain intact; this record does not delete or edit either.
 */
public record QuantityMeasurementCorrection(
        UUID id,
        UUID tenantId,
        UUID definitionId,
        UUID originalMeasurementId,
        UUID replacementMeasurementId,
        Instant recordedAt,
        String recordedBy,
        String reason,
        String idempotencyKey) {

    public QuantityMeasurementCorrection {
        Objects.requireNonNull(id, "Correction id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(originalMeasurementId, "Original measurement id cannot be null");
        Objects.requireNonNull(replacementMeasurementId, "Replacement measurement id cannot be null");
        Objects.requireNonNull(recordedAt, "Recorded timestamp cannot be null");
        recordedBy = required(recordedBy, "Recorded by");
        reason = required(reason, "Correction reason");
        idempotencyKey = required(idempotencyKey, "Idempotency key");
        if (originalMeasurementId.equals(replacementMeasurementId)) {
            throw new IllegalArgumentException("A correction must reference a different replacement measurement");
        }
        if (recordedBy.length() > 240 || reason.length() > 2000 || idempotencyKey.length() > 240) {
            throw new IllegalArgumentException("Correction field exceeds supported length");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
