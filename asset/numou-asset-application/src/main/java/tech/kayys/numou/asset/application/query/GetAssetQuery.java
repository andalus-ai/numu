package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Objects;

public record GetAssetQuery(String tenantId, AssetId assetId) {
    public GetAssetQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
