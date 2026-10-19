package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.valueobject.AssetStatus;
import tech.kayys.numou.asset.domain.valueobject.AssetType;

import java.util.Objects;

/** Filter + pagination criteria for asset search (see ASSET-14). */
public record AssetSearchCriteria(
        String tenantId,
        AssetStatus status,
        AssetType type,
        String locationId,
        String partyId,
        int page,
        int size
) {
    public AssetSearchCriteria {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, 200);
    }

    public static AssetSearchCriteria of(String tenantId) {
        return new AssetSearchCriteria(tenantId, null, null, null, null, 0, 20);
    }
}
