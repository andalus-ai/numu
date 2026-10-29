package tech.kayys.numou.pos.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable integration event envelope persisted alongside the business mutation.
 * Payload is JSON so consumers can evolve independently; schemaVersion is explicit.
 */
public record PosOutboxEvent(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String payloadJson) {

    public PosOutboxEvent {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        payloadJson = requireText(payloadJson, "payloadJson");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
