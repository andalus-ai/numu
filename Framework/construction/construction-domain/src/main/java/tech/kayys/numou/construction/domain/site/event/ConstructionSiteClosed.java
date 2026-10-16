package tech.kayys.syirkah.construction.domain.site.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionSiteClosed(
        UUID eventId,
        Instant occurredAt,
        UUID siteId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.site-closed"; }
}
