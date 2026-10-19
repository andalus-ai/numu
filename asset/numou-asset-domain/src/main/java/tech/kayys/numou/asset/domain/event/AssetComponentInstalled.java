package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a component is installed on a parent asset (see ASSET-18). */
public record AssetComponentInstalled(
        UUID eventId,
        Instant occurredAt,
        UUID componentAssetId,
        UUID parentAssetId,
        AssetRelationshipType relationshipType,
        UUID installationId,
        String installedBy
) implements DomainEvent {

    public AssetComponentInstalled {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(componentAssetId, "componentAssetId cannot be null");
        Objects.requireNonNull(parentAssetId, "parentAssetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
        Objects.requireNonNull(installationId, "installationId cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.component-installed";
    }
}
