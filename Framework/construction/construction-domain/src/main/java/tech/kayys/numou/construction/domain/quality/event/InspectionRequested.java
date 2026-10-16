package tech.kayys.syirkah.construction.domain.quality.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record InspectionRequested(
        UUID eventId,
        Instant occurredAt,
        UUID requestId,
        UUID siteId,
        UUID wbsNodeId
) implements DomainEvent {
    @Override public String eventType() { return "construction.inspection-requested"; }
}
