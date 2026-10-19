package tech.kayys.numou.survey.adapter.memory;

import tech.kayys.numou.survey.application.port.out.TenantRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.tenant.Tenant;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantRepository implements TenantRepository {
    private final Map<TenantId, Tenant> store = new ConcurrentHashMap<>();

    @Override public Optional<Tenant> findById(TenantId id) { return Optional.ofNullable(store.get(id)); }
    @Override public void save(Tenant t) { store.put(t.id(), t); }
    @Override public List<Tenant> findAll() { return List.copyOf(store.values()); }
}
