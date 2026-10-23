package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable review decision for a proposed quantity correction. */
public record QuantityCorrectionDecision(
        UUID id,
        UUID tenantId,
        UUID definitionId,
        UUID correctionId,
        Decision decision,
        Instant decidedAt,
        String decidedBy,
        String reason,
        String idempotencyKey) {

    public enum Decision { APPROVED, REJECTED }

    public QuantityCorrectionDecision {
        Objects.requireNonNull(id, "Decision id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(correctionId, "Correction id cannot be null");
        Objects.requireNonNull(decision, "Decision cannot be null");
        Objects.requireNonNull(decidedAt, "Decision timestamp cannot be null");
        decidedBy = required(decidedBy, "Decided by");
        reason = required(reason, "Decision reason");
        idempotencyKey = required(idempotencyKey, "Idempotency key");
        if (decidedBy.length() > 240 || reason.length() > 2000 || idempotencyKey.length() > 240) {
            throw new IllegalArgumentException("Decision field exceeds supported length");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
