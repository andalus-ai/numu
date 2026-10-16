package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when an asset transitions to {@code RETIRED}. */
public record AssetRetired(
        UUID eventId,
        Instant occurredAt,
        UUID assetId
) implements DomainEvent {

    public AssetRetired {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-retired";
    }
}
