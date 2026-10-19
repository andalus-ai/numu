package tech.kayys.numou.workforce.domain.performance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;

import java.time.Instant;
import java.util.UUID;

public record PerformanceCycleClosed(
        UUID eventId,
        Instant occurredAt,
        PerformanceCycleId id
) implements DomainEvent {
    public PerformanceCycleClosed(PerformanceCycleId id) {
        this(UUID.randomUUID(), Instant.now(), id);
    }
    @Override public String eventType() { return "workforce.performance.cycle.closed"; }
}
