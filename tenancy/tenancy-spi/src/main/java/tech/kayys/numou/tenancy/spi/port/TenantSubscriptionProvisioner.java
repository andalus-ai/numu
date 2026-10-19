package tech.kayys.numou.tenancy.spi.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * SPI port for the Subscription provisioning step.
 * Each implementation must be idempotent — "ensure X exists" not "create X blindly".
 */
public interface TenantSubscriptionProvisioner {

    void provision(TenantId tenantId);
}
