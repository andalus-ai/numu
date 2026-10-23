package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence contract for linehaul/hub transfer manifests. */
public interface HubTransferRepository {
    CompletionStage<HubTransfer> save(TenantId tenantId, HubTransfer transfer);
    CompletionStage<Optional<HubTransfer>> findById(TenantId tenantId, HubId transferId);
    CompletionStage<Optional<HubTransfer>> findByReference(TenantId tenantId, String reference);
}
