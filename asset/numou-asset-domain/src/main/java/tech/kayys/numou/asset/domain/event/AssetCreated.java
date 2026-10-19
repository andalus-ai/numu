package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a new asset is created (in {@code DRAFT} status). */
public record AssetCreated(
        UUID eventId,
        Instant occurredAt,
        UUID assetId,
        String assetNumber
) implements DomainEvent {

    public AssetCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(assetNumber, "assetNumber cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-created";
    }
}
