package tech.kayys.numou.reservation.adapter.postgres;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable delivery envelope returned from a PostgreSQL outbox lease claim. */
public record ReservationOutboxMessage(
        UUID eventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payloadJson,
        Instant occurredAt,
        int attempts,
        String leaseOwner
) {
    public ReservationOutboxMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(leaseOwner, "leaseOwner");
        if (attempts < 1) throw new IllegalArgumentException("attempts must be positive");
    }
}
