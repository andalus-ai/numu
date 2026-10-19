package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a component is removed from its parent asset (see ASSET-18). */
public record AssetComponentRemoved(
        UUID eventId,
        Instant occurredAt,
        UUID componentAssetId,
        UUID parentAssetId,
        UUID installationId,
        String removedBy
) implements DomainEvent {

    public AssetComponentRemoved {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(componentAssetId, "componentAssetId cannot be null");
        Objects.requireNonNull(parentAssetId, "parentAssetId cannot be null");
        Objects.requireNonNull(installationId, "installationId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.component-removed";
    }
}
