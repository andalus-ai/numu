package tech.kayys.syirkah.asset.spi.asset;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Inbound port other bounded contexts (FMS, Project, Accounting) may use to
 * verify/read asset facts without depending on the Asset internals.
 */
public interface AssetReader {

    CompletionStage<Boolean> exists(String tenantId, AssetId assetId);

    CompletionStage<Optional<AssetSummary>> findSummary(String tenantId, AssetId assetId);
}
