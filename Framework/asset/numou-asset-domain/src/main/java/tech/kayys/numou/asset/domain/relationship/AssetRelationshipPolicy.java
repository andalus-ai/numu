package tech.kayys.syirkah.asset.domain.relationship;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain policy for asset relationships (ASSET-17 §17.8).
 *
 * <p>Validates the persistence-free part of relationship integrity: null
 * checks and self-relation rejection. Graph traversal (duplicate, parent,
 * cycle checks) stays in the application/infrastructure layers.</p>
 */
public final class AssetRelationshipPolicy {

    private AssetRelationshipPolicy() {
    }

    public static void validateNewRelationship(
            UUID sourceAssetId,
            UUID targetAssetId,
            AssetRelationshipType type) {
        Objects.requireNonNull(sourceAssetId, "sourceAssetId cannot be null");
        Objects.requireNonNull(targetAssetId, "targetAssetId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        if (sourceAssetId.equals(targetAssetId)) {
            throw new IllegalArgumentException("An asset cannot be related to itself");
        }
    }
}
