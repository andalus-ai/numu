package tech.kayys.numou.asset.application.query;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Read-side (query) port, deliberately separate from the write-side
 * {@code AssetRepository} (see ASSET-14). Always tenant-scoped.
 */
public interface AssetReadRepository {

    CompletionStage<Optional<AssetView>> findById(String tenantId, UUID assetId);

    CompletionStage<AssetPage<AssetView>> search(AssetSearchCriteria criteria);
}
