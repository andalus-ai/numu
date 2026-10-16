package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.finance.AssetAccountingReconciliation;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Port for reconciliation entries (ASSET-25 §34). */
public interface AssetAccountingReconciliationRepository {

    CompletionStage<AssetAccountingReconciliation> save(AssetAccountingReconciliation entry);

    CompletionStage<List<AssetAccountingReconciliation>> findByAsset(String tenantId, AssetId assetId);

    CompletionStage<List<AssetAccountingReconciliation>> findAll(String tenantId);

    CompletionStage<Optional<AssetAccountingReconciliation>> findLatestByAsset(String tenantId, AssetId assetId);
}
