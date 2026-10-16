package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;
import tech.kayys.syirkah.asset.domain.model.Asset;
import tech.kayys.syirkah.foundation.domain.repository.Repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Outbound persistence port for the {@link Asset} aggregate.
 *
 * <p>Tenant isolation is <strong>structural</strong>: the tenant-scoped
 * methods exist so a caller can never load or mutate another tenant's asset
 * merely by knowing its UUID (see ASSET-09 / ASSET-10).</p>
 */
public interface AssetRepository extends Repository<Asset, AssetId> {

    CompletionStage<Boolean> existsByAssetNumber(String tenantId, String assetNumber);

    CompletionStage<Optional<Asset>> findByTenantAndId(String tenantId, AssetId assetId);

    CompletionStage<Boolean> existsByTenantAndId(String tenantId, AssetId assetId);

    CompletionStage<Void> deleteByTenantAndId(String tenantId, AssetId assetId);
}
