package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WarrantyClaimCompleted(UUID eventId, Instant occurredAt, UUID claimId) implements DomainEvent {
    public WarrantyClaimCompleted { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(claimId); }
    @Override public String eventType() { return "asset.warranty-claim-completed"; }
}
