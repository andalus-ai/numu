package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.warranty.AssetWarranty;
import tech.kayys.syirkah.asset.domain.warranty.AssetWarrantyId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped outbound port for warranties (ASSET-23). */
public interface AssetWarrantyRepository {
    CompletionStage<Optional<AssetWarranty>> findByTenantAndId(String tenantId, AssetWarrantyId id);
    CompletionStage<List<AssetWarranty>> findByAsset(String tenantId, UUID assetId);
    CompletionStage<Boolean> existsByTenantAndWarrantyNumber(String tenantId, String warrantyNumber);
    CompletionStage<Void> save(AssetWarranty warranty);
}
