package tech.kayys.numou.asset.infrastructure.persistence;

import tech.kayys.numou.asset.domain.classification.AssetClassification;
import tech.kayys.numou.asset.domain.custody.AssetCustody;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.location.AssetLocation;
import tech.kayys.numou.asset.domain.model.Asset;

/** Maps between the persistence entity and the domain aggregate (ASSET-09). */
public final class AssetEntityMapper {

    private AssetEntityMapper() {
    }

    public static AssetEntity toEntity(Asset asset) {
        AssetEntity entity = new AssetEntity();
        entity.id = asset.id().value();
        entity.tenantId = asset.tenantId();
        entity.assetNumber = asset.assetNumber();
        entity.name = asset.name();
        entity.assetType = asset.type();
        entity.status = asset.status();

        AssetLocation location = asset.location();
        if (location != null) {
            entity.locationId = location.locationId();
            entity.locationName = location.name();
        }
        AssetCustody custody = asset.custody();
        if (custody != null) {
            entity.partyId = custody.partyId();
            entity.partyType = custody.partyType();
            entity.partyName = custody.partyName();
        }
        AssetClassification classification = asset.classification();
        if (classification != null) {
            entity.classificationId = classification.classificationId();
            entity.classificationName = classification.name();
        }
        entity.createdAt = asset.getCreatedAt();
        entity.updatedAt = asset.getUpdatedAt();
        return entity;
    }

    public static Asset toDomain(AssetEntity entity) {
        AssetLocation location = entity.locationId == null ? null : AssetLocation.of(
                entity.locationId,
                entity.locationName == null ? entity.locationId : entity.locationName);
        AssetCustody custody = entity.partyId == null ? null : AssetCustody.of(
                entity.partyId,
                entity.partyType == null ? "UNKNOWN" : entity.partyType,
                entity.partyName);
        AssetClassification classification = entity.classificationId == null ? null : AssetClassification.of(
                entity.classificationId,
                entity.classificationName == null ? entity.classificationId : entity.classificationName);

        Asset asset = Asset.reconstitute(
                AssetId.of(entity.id), entity.tenantId, entity.assetNumber, entity.name,
                entity.assetType, entity.status, location, custody, classification);
        if (entity.createdAt != null) {
            asset.setCreatedAt(entity.createdAt);
        }
        if (entity.updatedAt != null) {
            asset.setUpdatedAt(entity.updatedAt);
        }
        if (entity.version != null) {
            asset.setVersion(entity.version.intValue());
        }
        return asset;
    }
}
