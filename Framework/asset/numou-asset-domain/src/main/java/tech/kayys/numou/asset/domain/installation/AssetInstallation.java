package tech.kayys.syirkah.asset.domain.installation;

import tech.kayys.syirkah.asset.domain.relationship.AssetRelationshipType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Historical installation record (see ASSET-18).
 *
 * <p>Once created the record is never mutated in place: removal produces a
 * terminal copy via {@link #markRemoved(Instant, String)} which only allows a
 * single {@code INSTALLED -> REMOVED} transition.</p>
 */
public record AssetInstallation(
        AssetInstallationId id,
        String tenantId,
        UUID componentAssetId,
        UUID parentAssetId,
        AssetRelationshipType relationshipType,
        Instant installedAt,
        String installedBy,
        AssetInstallationStatus status,
        Instant removedAt,
        String removedBy
) {

    public AssetInstallation {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(componentAssetId, "componentAssetId cannot be null");
        Objects.requireNonNull(parentAssetId, "parentAssetId cannot be null");
        Objects.requireNonNull(relationshipType, "relationshipType cannot be null");
        Objects.requireNonNull(installedAt, "installedAt cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (componentAssetId.equals(parentAssetId)) {
            throw new IllegalArgumentException("An asset cannot be installed on itself");
        }
        if (relationshipType == AssetRelationshipType.LINKED) {
            throw new IllegalArgumentException("LINKED is not a valid installation relationship");
        }
        if (status == AssetInstallationStatus.INSTALLED && (removedAt != null || removedBy != null)) {
            throw new IllegalArgumentException("An installed record cannot carry removal data");
        }
        if (status == AssetInstallationStatus.REMOVED && (removedAt == null || removedBy == null)) {
            throw new IllegalArgumentException("A removed record must carry removal data");
        }
    }

    public static AssetInstallation install(
            String tenantId,
            UUID componentAssetId,
            UUID parentAssetId,
            AssetRelationshipType relationshipType,
            Instant installedAt,
            String installedBy
    ) {
        return new AssetInstallation(
                AssetInstallationId.generate(), tenantId, componentAssetId, parentAssetId,
                relationshipType, installedAt, installedBy, AssetInstallationStatus.INSTALLED, null, null);
    }

    public AssetInstallation markRemoved(Instant when, String actor) {
        Objects.requireNonNull(when, "removedAt cannot be null");
        Objects.requireNonNull(actor, "removedBy cannot be null");
        if (status != AssetInstallationStatus.INSTALLED) {
            throw new IllegalStateException("Installation is already removed");
        }
        return new AssetInstallation(
                id, tenantId, componentAssetId, parentAssetId,
                relationshipType, installedAt, installedBy,
                AssetInstallationStatus.REMOVED, when, actor);
    }
}
