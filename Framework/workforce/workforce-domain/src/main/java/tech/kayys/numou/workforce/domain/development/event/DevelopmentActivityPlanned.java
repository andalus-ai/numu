package tech.kayys.numou.workforce.domain.development.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.development.DevelopmentActivityId;
import tech.kayys.numou.workforce.domain.development.DevelopmentActivityType;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record DevelopmentActivityPlanned(
        UUID eventId,
        Instant occurredAt,
        DevelopmentActivityId id,
        WorkerId workerId,
        DevelopmentActivityType type
) implements DomainEvent {
    public DevelopmentActivityPlanned(DevelopmentActivityId id, WorkerId workerId, DevelopmentActivityType type) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, type);
    }

    @Override
    public String eventType() {
        return "workforce.development.activity.planned";
    }
}
