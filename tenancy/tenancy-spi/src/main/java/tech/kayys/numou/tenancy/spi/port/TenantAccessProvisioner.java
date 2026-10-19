package tech.kayys.numou.tenancy.spi.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * SPI port for the Access provisioning step.
 * Each implementation must be idempotent — "ensure X exists" not "create X blindly".
 */
public interface TenantAccessProvisioner {

    void provision(TenantId tenantId);
}
