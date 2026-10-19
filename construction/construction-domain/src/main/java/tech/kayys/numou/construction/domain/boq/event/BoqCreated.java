package tech.kayys.numou.construction.domain.boq.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record BoqCreated(
        UUID eventId,
        Instant occurredAt,
        UUID boqId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.boq-created"; }
}
