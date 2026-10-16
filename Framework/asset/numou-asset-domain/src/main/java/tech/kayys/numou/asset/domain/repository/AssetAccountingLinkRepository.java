package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.finance.AssetAccountingLink;
import tech.kayys.syirkah.asset.domain.finance.AssetAccountingLinkId;
import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Port for the asset→accounting link (ASSET-25 §29; link only). */
public interface AssetAccountingLinkRepository {

    CompletionStage<AssetAccountingLink> save(AssetAccountingLink link);

    CompletionStage<Optional<AssetAccountingLink>> findByAsset(String tenantId, AssetId assetId);

    CompletionStage<Optional<AssetAccountingLink>> findByAccountingAsset(String tenantId, UUID accountingAssetId);

    CompletionStage<Optional<AssetAccountingLink>> findById(String tenantId, AssetAccountingLinkId id);

    CompletionStage<List<AssetAccountingLink>> findAll(String tenantId);
}
