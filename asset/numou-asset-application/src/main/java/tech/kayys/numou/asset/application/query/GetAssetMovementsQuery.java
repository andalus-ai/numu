package tech.kayys.numou.asset.application.query;

import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Objects;

/**
 * Query for an asset's append-only movement history (ASSET-13 §13.13).
 *
 * <p>History is exposed as a query, never as another set of Asset mutations.</p>
 */
public record GetAssetMovementsQuery(String tenantId, AssetId assetId) {

    public GetAssetMovementsQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
