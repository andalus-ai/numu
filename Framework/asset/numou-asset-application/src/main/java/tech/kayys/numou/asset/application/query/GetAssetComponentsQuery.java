package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Objects;

/** Query for the direct components of an asset (ASSET-17 §17.11). */
public record GetAssetComponentsQuery(String tenantId, AssetId assetId) {

    public GetAssetComponentsQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
