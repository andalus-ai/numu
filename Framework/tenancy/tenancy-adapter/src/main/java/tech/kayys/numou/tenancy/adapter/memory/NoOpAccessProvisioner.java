package tech.kayys.syirkah.tenancy.adapter.memory;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.spi.port.TenantAccessProvisioner;

/**
 * No-op TenantAccessProvisioner — for in-memory / test environments.
 * Replace with real implementation in production adapters.
 */
public final class NoOpAccessProvisioner implements TenantAccessProvisioner {

    @Override
    public void provision(TenantId tenantId) {
        // no-op — test/dev stub
    }
}
