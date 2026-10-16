package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.warranty.WarrantyClaim;
import tech.kayys.syirkah.asset.domain.warranty.WarrantyClaimId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped outbound port for warranty claims (ASSET-23). */
public interface WarrantyClaimRepository {
    CompletionStage<Optional<WarrantyClaim>> findByTenantAndId(String tenantId, WarrantyClaimId id);
    CompletionStage<List<WarrantyClaim>> findByAsset(String tenantId, UUID assetId);
    CompletionStage<Boolean> existsByTenantAndClaimNumber(String tenantId, String claimNumber);
    CompletionStage<Void> save(WarrantyClaim claim);
}
