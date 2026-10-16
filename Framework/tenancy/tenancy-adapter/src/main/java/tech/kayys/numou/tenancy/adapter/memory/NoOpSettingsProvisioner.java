package tech.kayys.syirkah.tenancy.adapter.memory;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.spi.port.TenantSettingsProvisioner;

/**
 * No-op TenantSettingsProvisioner — for in-memory / test environments.
 * Replace with real implementation in production adapters.
 */
public final class NoOpSettingsProvisioner implements TenantSettingsProvisioner {

    @Override
    public void provision(TenantId tenantId) {
        // no-op — test/dev stub
    }
}
