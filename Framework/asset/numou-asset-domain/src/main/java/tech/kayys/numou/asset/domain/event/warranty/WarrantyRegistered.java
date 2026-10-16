package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WarrantyRegistered(UUID eventId, Instant occurredAt, UUID warrantyId, UUID assetId, String warrantyNumber) implements DomainEvent {
    public WarrantyRegistered { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(warrantyId); Objects.requireNonNull(assetId); Objects.requireNonNull(warrantyNumber); }
    @Override public String eventType() { return "asset.warranty-created"; }
}
