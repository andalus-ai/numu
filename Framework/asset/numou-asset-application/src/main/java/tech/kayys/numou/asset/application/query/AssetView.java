package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.model.Asset;
import tech.kayys.numou.asset.domain.valueobject.AssetStatus;
import tech.kayys.numou.asset.domain.valueobject.AssetType;

import java.time.Instant;
import java.util.UUID;

/** Read model / REST-facing view of an Asset (see ASSET-14). */
public record AssetView(
        UUID id,
        String tenantId,
        String assetNumber,
        String name,
        AssetType type,
        AssetStatus status,
        String locationId,
        String locationName,
        String partyId,
        String partyType,
        String partyName,
        String classificationId,
        String classificationName,
        Instant createdAt,
        Instant updatedAt
) {

    public static AssetView from(Asset asset) {
        var location = asset.location();
        var custody = asset.custody();
        var classification = asset.classification();
        return new AssetView(
                asset.id().value(),
                asset.tenantId(),
                asset.assetNumber(),
                asset.name(),
                asset.type(),
                asset.status(),
                location == null ? null : location.locationId(),
                location == null ? null : location.name(),
                custody == null ? null : custody.partyId(),
                custody == null ? null : custody.partyType(),
                custody == null ? null : custody.partyName(),
                classification == null ? null : classification.classificationId(),
                classification == null ? null : classification.name(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
