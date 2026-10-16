package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantStorageProvisioner;

/**
 * No-op TenantStorageProvisioner — for in-memory / test environments.
 * Replace with real implementation in production adapters.
 */
public final class NoOpStorageProvisioner implements TenantStorageProvisioner {

    @Override
    public void provision(TenantId tenantId) {
        // no-op — test/dev stub
    }
}
