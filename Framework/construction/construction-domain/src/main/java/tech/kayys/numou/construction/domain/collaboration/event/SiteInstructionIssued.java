package tech.kayys.syirkah.construction.domain.collaboration.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record SiteInstructionIssued(
        UUID eventId,
        Instant occurredAt,
        UUID instructionId,
        UUID siteId,
        String instructionNumber
) implements DomainEvent {
    @Override public String eventType() { return "construction.site-instruction-issued"; }
}
