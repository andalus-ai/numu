package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.inspection.AssetInspection;
import tech.kayys.syirkah.asset.domain.inspection.AssetInspectionId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped persistence port for inspections (ASSET-20 section 20.18). */
public interface AssetInspectionRepository {

    CompletionStage<Optional<AssetInspection>> findById(String tenantId, AssetInspectionId inspectionId);

    CompletionStage<AssetInspection> save(String tenantId, AssetInspection inspection);

    CompletionStage<List<AssetInspection>> findByAssetId(String tenantId, UUID assetId);
}
