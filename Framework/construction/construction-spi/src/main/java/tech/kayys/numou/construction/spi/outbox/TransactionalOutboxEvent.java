package tech.kayys.syirkah.construction.spi.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable event envelope inserted inside the business command transaction. */
public record TransactionalOutboxEvent(
        UUID eventId,
        String tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payloadJson,
        Instant occurredAt) {

    public TransactionalOutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireText(tenantId, "tenantId");
        requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        requireText(eventType, "eventType");
        requireText(payloadJson, "payloadJson");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
