package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.tenancy.domain.provisioning.TenantProvisioning;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantProvisioningRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantProvisioningRepository implements TenantProvisioningRepository {

    private final Map<TenantId, TenantProvisioning> store = new ConcurrentHashMap<>();

    @Override
    public TenantProvisioning save(TenantProvisioning provisioning) {
        store.put(provisioning.tenantId(), provisioning);
        return provisioning;
    }

    @Override
    public Optional<TenantProvisioning> findByTenantId(TenantId tenantId) {
        return Optional.ofNullable(store.get(tenantId));
    }
}
