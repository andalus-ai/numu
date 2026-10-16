package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when custody of an asset is cleared (see ASSET-12). */
public record AssetUnassigned(
        UUID eventId,
        Instant occurredAt,
        UUID assetId
) implements DomainEvent {

    public AssetUnassigned {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-unassigned";
    }
}
