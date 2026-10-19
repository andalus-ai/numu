package tech.kayys.numou.workforce.domain.position.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.time.Instant;
import java.util.UUID;

public record PositionActivated(
        UUID eventId,
        Instant occurredAt,
        PositionId positionId
) implements DomainEvent {

    public PositionActivated(PositionId positionId) {
        this(UUID.randomUUID(), Instant.now(), positionId);
    }

    @Override
    public String eventType() {
        return "workforce.position.activated";
    }
}
