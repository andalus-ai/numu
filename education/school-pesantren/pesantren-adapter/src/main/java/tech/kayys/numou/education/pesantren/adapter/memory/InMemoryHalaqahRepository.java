package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.Halaqah;
import tech.kayys.numou.education.pesantren.domain.HalaqahId;
import tech.kayys.numou.education.pesantren.spi.HalaqahRepository;

public class InMemoryHalaqahRepository implements HalaqahRepository {
    private final Map<HalaqahId, Halaqah> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Halaqah> save(Halaqah e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Halaqah>> findById(HalaqahId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(HalaqahId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Halaqah e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(HalaqahId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
