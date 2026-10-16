package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PerformanceReviewFinalized(
        UUID eventId,
        Instant occurredAt,
        PerformanceReviewId id,
        WorkerId workerId,
        BigDecimal overallScore
) implements DomainEvent {
    public PerformanceReviewFinalized(PerformanceReviewId id, WorkerId workerId, BigDecimal overallScore) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, overallScore);
    }
    @Override public String eventType() { return "workforce.performance.review.finalized"; }
}
