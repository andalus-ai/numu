package tech.kayys.numou.tenancy.spi.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * SPI port for the Settings provisioning step.
 * Each implementation must be idempotent — "ensure X exists" not "create X blindly".
 */
public interface TenantSettingsProvisioner {

    void provision(TenantId tenantId);
}
