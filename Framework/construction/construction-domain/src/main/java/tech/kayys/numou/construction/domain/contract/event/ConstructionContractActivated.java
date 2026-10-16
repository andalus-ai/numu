package tech.kayys.syirkah.construction.domain.contract.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionContractActivated(
        UUID eventId,
        Instant occurredAt,
        UUID contractId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.contract-activated"; }
}
