package tech.kayys.syirkah.construction.domain.contract.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ConstructionContractCreated(
        UUID eventId,
        Instant occurredAt,
        UUID contractId,
        UUID projectId,
        String contractNumber
) implements DomainEvent {
    @Override public String eventType() { return "construction.contract-created"; }
}
