package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceReviewApproved(
        UUID eventId,
        Instant occurredAt,
        PerformanceReviewId id,
        WorkerId workerId
) implements DomainEvent {
    public PerformanceReviewApproved(PerformanceReviewId id, WorkerId workerId) {
        this(UUID.randomUUID(), Instant.now(), id, workerId);
    }
    @Override public String eventType() { return "workforce.performance.review.approved"; }
}
