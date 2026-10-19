package tech.kayys.numou.asset.application.inspection;

import java.util.Objects;
import java.util.UUID;

/** Query: current condition for an asset = latest COMPLETED inspection. */
public record GetAssetConditionQuery(String tenantId, UUID assetId) {
    public GetAssetConditionQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
