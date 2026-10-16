package tech.kayys.syirkah.construction.domain.procurement.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record MaterialRequirementCreated(
        UUID eventId,
        Instant occurredAt,
        UUID requirementId,
        UUID projectId,
        UUID siteId
) implements DomainEvent {
    @Override public String eventType() { return "construction.material-requirement-created"; }
}
