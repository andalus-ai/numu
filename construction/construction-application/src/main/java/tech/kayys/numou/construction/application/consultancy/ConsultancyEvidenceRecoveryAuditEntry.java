package tech.kayys.numou.construction.application.consultancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable operator recovery event; never exposes persistence entities. */
public record ConsultancyEvidenceRecoveryAuditEntry(
        UUID auditId,
        UUID operationId,
        UUID actorId,
        String reason,
        String idempotencyKey,
        ConsultancyEvidenceOperation.Status previousStatus,
        ConsultancyEvidenceOperation.Status resultingStatus,
        Instant occurredAt) {
    public ConsultancyEvidenceRecoveryAuditEntry {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(actorId, "actorId");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        Objects.requireNonNull(previousStatus, "previousStatus");
        Objects.requireNonNull(resultingStatus, "resultingStatus");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (previousStatus != ConsultancyEvidenceOperation.Status.TERMINAL_FAILURE
                || resultingStatus != ConsultancyEvidenceOperation.Status.PENDING) {
            throw new IllegalArgumentException("Unsupported recovery transition");
        }
    }
}
