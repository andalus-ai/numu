package tech.kayys.numou.asset.domain.repository;

import tech.kayys.numou.asset.domain.finance.AssetFinancialSnapshot;
import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Port for the financial-snapshot projection (ASSET-25 §26, read model). */
public interface AssetFinancialSnapshotRepository {

    CompletionStage<AssetFinancialSnapshot> save(String tenantId, AssetFinancialSnapshot snapshot,
            String sourceEventKey, String sourceKind);

    CompletionStage<Optional<AssetFinancialSnapshot>> findByAsset(String tenantId, AssetId assetId);

    CompletionStage<Boolean> existsBySourceEvent(String tenantId, String sourceEventKey);
}
