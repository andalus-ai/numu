package tech.kayys.syirkah.construction.domain.handover.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionHandedOver(
        UUID eventId,
        Instant occurredAt,
        UUID completionId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.handed-over"; }
}
