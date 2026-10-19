package tech.kayys.numou.tenancy.spi.port;

import tech.kayys.numou.tenancy.domain.provisioning.TenantProvisioning;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Repository port for TenantProvisioning (exactly one per Tenant). */
public interface TenantProvisioningRepository {

    TenantProvisioning save(TenantProvisioning provisioning);

    Optional<TenantProvisioning> findByTenantId(TenantId tenantId);
}
