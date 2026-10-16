package tech.kayys.syirkah.construction.domain.document.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record RfiSubmitted(
        UUID eventId,
        Instant occurredAt,
        UUID rfiId,
        UUID projectId,
        String rfiNumber
) implements DomainEvent {
    @Override public String eventType() { return "construction.rfi-submitted"; }
}
