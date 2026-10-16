package tech.kayys.numou.asset.domain.repository;

import tech.kayys.numou.asset.domain.classification.AssetAttribute;
import tech.kayys.numou.asset.domain.identifier.AssetId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Port for dynamic asset metadata / attributes (see ASSET-15). */
public interface AssetMetadataRepository {

    CompletionStage<Void> replaceAttributes(
            String tenantId,
            AssetId assetId,
            List<AssetAttribute> attributes
    );

    CompletionStage<List<AssetAttribute>> findByAsset(String tenantId, AssetId assetId);
}
