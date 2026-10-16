package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PerformanceGoalProgressRecorded(
        UUID eventId,
        Instant occurredAt,
        PerformanceGoalId id,
        BigDecimal value
) implements DomainEvent {
    public PerformanceGoalProgressRecorded(PerformanceGoalId id, BigDecimal value) {
        this(UUID.randomUUID(), Instant.now(), id, value);
    }
    @Override public String eventType() { return "workforce.performance.goal.progress.recorded"; }
}
