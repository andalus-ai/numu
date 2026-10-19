package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantSubscriptionProvisioner;

/**
 * No-op TenantSubscriptionProvisioner — for in-memory / test environments.
 * Replace with real implementation in production adapters.
 */
public final class NoOpSubscriptionProvisioner implements TenantSubscriptionProvisioner {

    @Override
    public void provision(TenantId tenantId) {
        // no-op — test/dev stub
    }
}
