package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.tenancy.domain.settings.TenantSettings;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantSettingsRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantSettingsRepository implements TenantSettingsRepository {

    private final Map<TenantId, TenantSettings> store = new ConcurrentHashMap<>();

    @Override
    public TenantSettings save(TenantSettings settings) {
        store.put(settings.tenantId(), settings);
        return settings;
    }

    @Override
    public Optional<TenantSettings> findByTenantId(TenantId tenantId) {
        return Optional.ofNullable(store.get(tenantId));
    }

    @Override
    public boolean existsByTenantId(TenantId tenantId) {
        return store.containsKey(tenantId);
    }
}
