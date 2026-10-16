package tech.kayys.syirkah.tenancy.spi.port;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

/**
 * SPI port for the Feature provisioning step.
 * Each implementation must be idempotent — "ensure X exists" not "create X blindly".
 */
public interface TenantFeatureProvisioner {

    void provision(TenantId tenantId);
}
