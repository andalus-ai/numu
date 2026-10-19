package tech.kayys.numou.tenancy.adapter.memory;

import tech.kayys.numou.tenancy.domain.tenant.Tenant;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.tenancy.spi.port.TenantRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory TenantRepository for local/test use. */
public final class InMemoryTenantRepository implements TenantRepository {

    private final Map<TenantId, Tenant> store = new ConcurrentHashMap<>();

    @Override
    public Tenant save(Tenant tenant) {
        store.put(tenant.id(), tenant);
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        return Optional.ofNullable(store.get(tenantId));
    }

    @Override
    public Optional<Tenant> findByCode(String code) {
        return store.values().stream()
                .filter(t -> t.code().equals(code))
                .findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
        return store.values().stream().anyMatch(t -> t.code().equals(code));
    }
}
