package tech.kayys.numou.queuemanagement.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Minimal append-only audit facts; avoid copying sensitive source-record payloads into queue audit. */
public record QueueAuditRecord(
        TenantId tenantId,
        String actorId,
        String action,
        String resourceType,
        String resourceId,
        String correlationId,
        Instant occurredAt) {
    public QueueAuditRecord {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        actorId = required(actorId, "actorId");
        action = required(action, "action");
        resourceType = required(resourceType, "resourceType");
        resourceId = required(resourceId, "resourceId");
        correlationId = required(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}