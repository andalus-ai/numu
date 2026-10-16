package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.installation.AssetInstallation;
import tech.kayys.syirkah.asset.domain.installation.AssetInstallationId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-aware persistence port for installation history (see ASSET-18). */
public interface AssetInstallationRepository {

    CompletionStage<AssetInstallation> save(AssetInstallation installation);

    CompletionStage<Optional<AssetInstallation>> findById(String tenantId, AssetInstallationId id);

    CompletionStage<Optional<AssetInstallation>> findActive(String tenantId, UUID componentAssetId, UUID parentAssetId);

    CompletionStage<List<AssetInstallation>> findHistory(String tenantId, UUID assetId);

    CompletionStage<Boolean> existsActive(String tenantId, UUID componentAssetId);
}
