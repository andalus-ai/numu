package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceGoalActivated(
        UUID eventId,
        Instant occurredAt,
        PerformanceGoalId id
) implements DomainEvent {
    public PerformanceGoalActivated(PerformanceGoalId id) {
        this(UUID.randomUUID(), Instant.now(), id);
    }
    @Override public String eventType() { return "workforce.performance.goal.activated"; }
}
