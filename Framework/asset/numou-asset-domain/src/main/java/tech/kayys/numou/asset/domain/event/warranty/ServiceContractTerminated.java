package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ServiceContractTerminated(UUID eventId, Instant occurredAt, UUID contractId) implements DomainEvent {
    public ServiceContractTerminated { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(contractId); }
    @Override public String eventType() { return "asset.service-contract-terminated"; }
}
