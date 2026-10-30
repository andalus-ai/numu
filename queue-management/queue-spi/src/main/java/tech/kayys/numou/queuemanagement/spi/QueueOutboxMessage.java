package tech.kayys.numou.queuemanagement.spi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable event envelope leased to one dispatcher attempt. */
public record QueueOutboxMessage(
        UUID eventId, UUID tenantId, String eventType, int schemaVersion,
        String aggregateType, String aggregateId, String correlationId,
        Instant occurredAt, String payload, int attemptCount,
        String leaseOwner, UUID leaseToken, Instant leaseUntil) {
    public QueueOutboxMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(leaseToken, "leaseToken");
        Objects.requireNonNull(leaseUntil, "leaseUntil");
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be positive");
        if (leaseOwner == null || leaseOwner.isBlank()) throw new IllegalArgumentException("leaseOwner must not be blank");
    }
}
