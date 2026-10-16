package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.availability.AssetUtilizationRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped outbound port for utilization observations (ASSET-26 §15).
 */
public interface AssetUtilizationRepository {

    CompletionStage<AssetUtilizationRecord> save(String tenantId, AssetUtilizationRecord record);

    CompletionStage<Optional<AssetUtilizationRecord>> findBySourceRef(
            String tenantId, String source, String referenceId);

    CompletionStage<List<AssetUtilizationRecord>> findByAssetId(
            String tenantId, UUID assetId, Instant from, Instant to);

    default CompletionStage<Boolean> existsBySourceRef(String tenantId, String source, String referenceId) {
        return findBySourceRef(tenantId, source, referenceId).thenApply(Optional::isPresent);
    }
}