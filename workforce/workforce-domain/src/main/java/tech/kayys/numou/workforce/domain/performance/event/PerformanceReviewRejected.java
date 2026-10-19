package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceReviewRejected(
        UUID eventId,
        Instant occurredAt,
        PerformanceReviewId id,
        String reason
) implements DomainEvent {
    public PerformanceReviewRejected(PerformanceReviewId id, String reason) {
        this(UUID.randomUUID(), Instant.now(), id, reason);
    }
    @Override public String eventType() { return "workforce.performance.review.rejected"; }
}
