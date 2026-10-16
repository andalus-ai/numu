package tech.kayys.syirkah.construction.domain.quality.event;

import tech.kayys.syirkah.construction.domain.quality.NcrSeverity;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record NcrCreated(
        UUID eventId,
        Instant occurredAt,
        UUID ncrId,
        UUID siteId,
        String ncrNumber,
        NcrSeverity severity
) implements DomainEvent {
    @Override public String eventType() { return "construction.ncr-created"; }
}
