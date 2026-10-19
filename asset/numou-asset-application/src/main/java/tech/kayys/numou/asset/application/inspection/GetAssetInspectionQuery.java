package tech.kayys.numou.asset.application.inspection;

import java.util.Objects;
import java.util.UUID;

/** Query: load a single inspection read model. */
public record GetAssetInspectionQuery(String tenantId, UUID inspectionId) {
    public GetAssetInspectionQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
    }
}
