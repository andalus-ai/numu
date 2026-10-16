package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.meter.AssetMeter;
import tech.kayys.syirkah.asset.domain.meter.AssetMeterId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Outbound port for meter definitions (ASSET-21 §21.18). */
public interface AssetMeterRepository {

    CompletionStage<AssetMeter> save(String tenantId, AssetMeter meter);

    CompletionStage<Optional<AssetMeter>> findById(String tenantId, AssetMeterId meterId);

    CompletionStage<List<AssetMeter>> findByAssetId(String tenantId, UUID assetId);
}
