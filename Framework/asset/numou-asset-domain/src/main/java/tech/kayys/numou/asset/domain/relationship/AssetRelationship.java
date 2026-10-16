package tech.kayys.syirkah.asset.domain.relationship;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Instant;
import java.util.Objects;

/**
 * A directed, immutable relationship between two assets (see ASSET-16).
 *
 * <p>Kept as a small aggregate of its own rather than nested collections on
 * {@code Asset}; both referenced assets must be validated to belong to the
 * same tenant before the relationship is created.</p>
 */
public record AssetRelationship(
        AssetRelationshipId id,
        String tenantId,
        AssetId sourceAssetId,
        AssetId relatedAssetId,
        AssetRelationshipType type,
        Instant createdAt
) {

    public AssetRelationship {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(sourceAssetId, "sourceAssetId cannot be null");
        Objects.requireNonNull(relatedAssetId, "relatedAssetId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (sourceAssetId.equals(relatedAssetId)) {
            throw new IllegalArgumentException("an asset cannot be related to itself");
        }
    }

    public static AssetRelationship of(
            String tenantId,
            AssetId sourceAssetId,
            AssetId relatedAssetId,
            AssetRelationshipType type,
            Instant createdAt
    ) {
        return new AssetRelationship(
                AssetRelationshipId.generate(), tenantId,
                sourceAssetId, relatedAssetId, type, createdAt
        );
    }
}
