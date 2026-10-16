package tech.kayys.numou.asset.application.document;

import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Objects;

/** List all document references of one asset (tenant-scoped). */
public record GetAssetDocumentsQuery(String tenantId, AssetId assetId) {

    public GetAssetDocumentsQuery {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
