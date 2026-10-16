package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.warranty.ServiceContract;
import tech.kayys.syirkah.asset.domain.warranty.ServiceContractId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped outbound port for service contracts (ASSET-23). */
public interface ServiceContractRepository {
    CompletionStage<Optional<ServiceContract>> findByTenantAndId(String tenantId, ServiceContractId id);
    CompletionStage<List<ServiceContract>> findByAsset(String tenantId, UUID assetId);
    CompletionStage<Boolean> existsByTenantAndContractNumber(String tenantId, String contractNumber);
    CompletionStage<Void> save(ServiceContract contract);
}
