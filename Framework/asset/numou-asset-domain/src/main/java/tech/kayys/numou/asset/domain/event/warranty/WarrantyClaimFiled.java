package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WarrantyClaimFiled(UUID eventId, Instant occurredAt, UUID claimId, UUID warrantyId, String claimNumber) implements DomainEvent {
    public WarrantyClaimFiled { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(claimId); Objects.requireNonNull(warrantyId); Objects.requireNonNull(claimNumber); }
    @Override public String eventType() { return "asset.warranty-claim-created"; }
}
