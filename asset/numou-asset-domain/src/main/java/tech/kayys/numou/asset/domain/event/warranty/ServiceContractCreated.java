package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ServiceContractCreated(UUID eventId, Instant occurredAt, UUID contractId, String contractNumber) implements DomainEvent {
    public ServiceContractCreated { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(contractId); Objects.requireNonNull(contractNumber); }
    @Override public String eventType() { return "asset.service-contract-created"; }
}
