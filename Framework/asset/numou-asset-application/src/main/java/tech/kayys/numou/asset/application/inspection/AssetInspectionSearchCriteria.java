package tech.kayys.numou.asset.application.inspection;

import tech.kayys.numou.asset.domain.inspection.AssetCondition;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionStatus;
import tech.kayys.numou.asset.domain.inspection.InspectionResult;
import tech.kayys.numou.asset.domain.inspection.InspectionType;

import java.util.Objects;
import java.util.UUID;

/** Filter + pagination criteria for inspection search (ASSET-20 section 20.22). */
public record AssetInspectionSearchCriteria(
        String tenantId,
        UUID assetId,
        InspectionType type,
        AssetInspectionStatus status,
        AssetCondition condition,
        InspectionResult result,
        int page,
        int size
) {
    public AssetInspectionSearchCriteria {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, 200);
    }
}
