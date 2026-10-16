package tech.kayys.numou.asset.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Raised when a relationship between two assets is created (see ASSET-16). */
public record AssetRelationshipCreated(
        UUID eventId,
        Instant occurredAt,
        UUID sourceAssetId,
        UUID relatedAssetId,
        String relationshipType
) implements DomainEvent {

    public AssetRelationshipCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(sourceAssetId, "sourceAssetId cannot be null");
        Objects.requireNonNull(relatedAssetId, "relatedAssetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
    }

    @Override
    public String eventType() {
        return "asset.asset-relationship-created";
    }
}
