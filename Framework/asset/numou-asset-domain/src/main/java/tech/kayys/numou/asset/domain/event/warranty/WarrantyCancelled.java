package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WarrantyCancelled(UUID eventId, Instant occurredAt, UUID warrantyId) implements DomainEvent {
    public WarrantyCancelled { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(warrantyId); }
    @Override public String eventType() { return "asset.warranty-cancelled"; }
}
