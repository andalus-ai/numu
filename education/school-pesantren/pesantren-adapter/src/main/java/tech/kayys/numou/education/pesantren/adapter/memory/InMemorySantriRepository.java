package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.Santri;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.spi.SantriRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public class InMemorySantriRepository implements SantriRepository {
    private final Map<SantriId, Santri> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Santri> save(Santri e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Santri>> findById(SantriId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(SantriId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Santri e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(SantriId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<Santri>> findByNumber(TenantId tenantId, String santriNumber) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getTenantId().equals(tenantId) && s.getSantriNumber().equals(santriNumber)).findFirst());
    }
}
