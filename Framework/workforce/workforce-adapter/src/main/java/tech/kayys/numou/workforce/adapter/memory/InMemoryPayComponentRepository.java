package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponent;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;
import tech.kayys.numou.workforce.spi.port.PayComponentRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPayComponentRepository implements PayComponentRepository {

    private final Map<PayComponentId, PayComponent> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<PayComponent> save(PayComponent e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<PayComponent>> findById(PayComponentId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(PayComponentId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(PayComponent e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(PayComponentId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<PayComponent>> findByCode(TenantId tenantId, String code) {
        Optional<PayComponent> opt = store.values().stream()
                .filter(c -> c.tenantId().equals(tenantId) && c.code().equalsIgnoreCase(code))
                .findFirst();
        return CompletableFuture.completedFuture(opt);
    }

    @Override
    public CompletionStage<List<PayComponent>> findByTenant(TenantId tenantId) {
        List<PayComponent> list = store.values().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }
}
