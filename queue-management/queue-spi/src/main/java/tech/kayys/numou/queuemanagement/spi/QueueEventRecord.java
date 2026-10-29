package tech.kayys.numou.queuemanagement.spi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Versioned event envelope to be stored in a durable outbox in the same transaction as queue state. */
public record QueueEventRecord(
        TenantId tenantId,
        String eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        String correlationId,
        Instant occurredAt,
        String payload) {
    public QueueEventRecord {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        eventId = required(eventId, "eventId");
        eventType = required(eventType, "eventType");
        aggregateType = required(aggregateType, "aggregateType");
        aggregateId = required(aggregateId, "aggregateId");
        correlationId = required(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}