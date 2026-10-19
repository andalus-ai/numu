package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantFeatureProvisioner;

/**
 * No-op TenantFeatureProvisioner — for in-memory / test environments.
 * Replace with real implementation in production adapters.
 */
public final class NoOpFeatureProvisioner implements TenantFeatureProvisioner {

    @Override
    public void provision(TenantId tenantId) {
        // no-op — test/dev stub
    }
}
