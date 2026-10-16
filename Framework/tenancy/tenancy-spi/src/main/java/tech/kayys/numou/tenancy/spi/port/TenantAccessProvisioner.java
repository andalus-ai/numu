package tech.kayys.syirkah.tenancy.spi.port;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

/**
 * SPI port for the Access provisioning step.
 * Each implementation must be idempotent — "ensure X exists" not "create X blindly".
 */
public interface TenantAccessProvisioner {

    void provision(TenantId tenantId);
}
