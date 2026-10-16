package tech.kayys.syirkah.construction.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionClaimSubmitted(
        UUID eventId,
        Instant occurredAt,
        UUID claimId,
        UUID contractId,
        String claimReference
) implements DomainEvent {
    @Override public String eventType() { return "construction.claim-submitted"; }
}
