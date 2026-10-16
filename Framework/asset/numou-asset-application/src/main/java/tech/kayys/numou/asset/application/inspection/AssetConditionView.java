package tech.kayys.numou.asset.application.inspection;

import java.time.Instant;
import java.util.UUID;

/** Current-condition projection for an asset (ASSET-20 section 20.24). */
public record AssetConditionView(
        UUID assetId,
        tech.kayys.numou.asset.domain.inspection.AssetCondition condition,
        UUID inspectionId,
        Instant assessedAt
) {
}
