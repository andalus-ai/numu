package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Port for the financial-snapshot projection (ASSET-25 §26, read model). */
public interface AssetFinancialSnapshotRepository {

    CompletionStage<AssetFinancialSnapshot> save(String tenantId, AssetFinancialSnapshot snapshot,
            String sourceEventKey, String sourceKind);

    CompletionStage<Optional<AssetFinancialSnapshot>> findByAsset(String tenantId, AssetId assetId);

    CompletionStage<Boolean> existsBySourceEvent(String tenantId, String sourceEventKey);
}
