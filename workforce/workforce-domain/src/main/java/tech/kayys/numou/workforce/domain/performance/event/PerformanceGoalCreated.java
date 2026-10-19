package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceGoalCreated(
        UUID eventId,
        Instant occurredAt,
        PerformanceGoalId id,
        WorkerId workerId,
        PerformanceCycleId cycleId
) implements DomainEvent {
    public PerformanceGoalCreated(PerformanceGoalId id, WorkerId workerId, PerformanceCycleId cycleId) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, cycleId);
    }
    @Override public String eventType() { return "workforce.performance.goal.created"; }
}
