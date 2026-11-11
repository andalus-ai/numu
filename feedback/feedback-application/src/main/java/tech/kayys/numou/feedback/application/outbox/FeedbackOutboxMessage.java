package tech.kayys.numou.feedback.application.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable integration event claimed from the Feedback transactional outbox. */
public record FeedbackOutboxMessage(
        UUID eventId,
        UUID tenantId,
        UUID aggregateId,
        String eventType,
        int eventVersion,
        String payload,
        Instant occurredAt,
        int attempts,
        UUID leaseToken) {
    public FeedbackOutboxMessage {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (eventVersion < 1) throw new IllegalArgumentException("eventVersion must be positive");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload is required");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (attempts < 0) throw new IllegalArgumentException("attempts cannot be negative");
        Objects.requireNonNull(leaseToken, "leaseToken");
    }
}
