package tech.kayys.syirkah.asset.domain.movement;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, append-only historical fact about an asset's location,
 * custody or classification (see ASSET-13).
 *
 * <p>History is never mutated: a correction is a new movement, and the
 * {@code sourceEventId} links back to the domain event that produced it,
 * which makes the projection idempotent.</p>
 */
public record AssetMovement(
        AssetMovementId id,
        String tenantId,
        AssetId assetId,
        AssetMovementType type,
        Instant occurredAt,
        String fromLocationId,
        String fromPartyId,
        String toLocationId,
        String toPartyId,
        String classificationId,
        UUID relatedAssetId,
        UUID sourceEventId
) {

    public AssetMovement {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(sourceEventId, "sourceEventId cannot be null");
    }

    public static AssetMovement of(
            String tenantId,
            AssetId assetId,
            AssetMovementType type,
            Instant occurredAt,
            UUID sourceEventId
    ) {
        return new AssetMovement(
                AssetMovementId.generate(), tenantId, assetId, type,
                occurredAt, null, null, null, null, null, null, sourceEventId
        );
    }
}
