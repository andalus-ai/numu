package tech.kayys.syirkah.construction.domain.logistics.event;

import tech.kayys.syirkah.construction.domain.logistics.SiteZoneType;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record SiteZoneCreated(
        UUID eventId,
        Instant occurredAt,
        UUID zoneId,
        UUID siteId,
        String zoneCode,
        SiteZoneType type
) implements DomainEvent {
    @Override public String eventType() { return "construction.site-zone-created"; }
}
