package tech.kayys.numou.asset.domain.event.warranty;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WarrantyCoverageAdded(UUID eventId, Instant occurredAt, UUID warrantyId, UUID coverageId) implements DomainEvent {
    public WarrantyCoverageAdded { Objects.requireNonNull(eventId); Objects.requireNonNull(occurredAt); Objects.requireNonNull(warrantyId); Objects.requireNonNull(coverageId); }
    @Override public String eventType() { return "asset.warranty-coverage-added"; }
}
