package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Objects;

/** Query for the direct parent of an asset (ASSET-17 §17.12). */
public record GetAssetParentQuery(String tenantId, AssetId assetId) {

    public GetAssetParentQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
