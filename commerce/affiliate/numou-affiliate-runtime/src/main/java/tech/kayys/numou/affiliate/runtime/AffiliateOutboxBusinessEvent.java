package tech.kayys.numou.affiliate.runtime;

import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.valueobject.EventVersion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned integration representation of an Affiliate outbox record.
 * The original JSON payload is preserved verbatim; transport adapters do not
 * reinterpret or mutate the owning capability's payload schema.
 */
public record AffiliateOutboxBusinessEvent(
        UUID eventId,
        Instant occurredAt,
        String eventType,
        String eventKey,
        String aggregateType,
        UUID aggregateId,
        int attempts,
        String payload) implements BusinessEvent {

    public AffiliateOutboxBusinessEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(eventType, "eventType");
        requireText(eventKey, "eventKey");
        requireText(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be positive");
        Objects.requireNonNull(payload, "payload");
    }

    @Override
    public EventVersion eventVersion() {
        return EventVersion.V1;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}