package tech.kayys.numou.courier.adapter.postgres;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable event envelope passed to a configured external publication adapter. */
public record CourierPublishedEvent(
        UUID tenantId,
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateReference,
        Instant occurredAt,
        String payloadJson) {
    public CourierPublishedEvent {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (aggregateType == null || aggregateType.isBlank()) throw new IllegalArgumentException("aggregateType is required");
        if (aggregateReference == null || aggregateReference.isBlank()) throw new IllegalArgumentException("aggregateReference is required");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (payloadJson == null || payloadJson.isBlank()) throw new IllegalArgumentException("payloadJson is required");
    }
}
