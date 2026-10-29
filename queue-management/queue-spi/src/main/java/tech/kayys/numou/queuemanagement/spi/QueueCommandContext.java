package tech.kayys.numou.queuemanagement.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Authenticated command metadata passed explicitly; never inferred from static request context in domain code. */
public record QueueCommandContext(
        TenantId tenantId,
        String actorId,
        String correlationId,
        String idempotencyKey,
        Instant occurredAt) {
    public QueueCommandContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        actorId = required(actorId, "actorId", 200);
        correlationId = required(correlationId, "correlationId", 200);
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 200);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}