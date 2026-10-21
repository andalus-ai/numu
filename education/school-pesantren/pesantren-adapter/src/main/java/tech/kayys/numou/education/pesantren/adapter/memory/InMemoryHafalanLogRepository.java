package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.HafalanLog;
import tech.kayys.numou.education.pesantren.domain.HafalanLogId;
import tech.kayys.numou.education.pesantren.spi.HafalanLogRepository;

public class InMemoryHafalanLogRepository implements HafalanLogRepository {
    private final Map<HafalanLogId, HafalanLog> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<HafalanLog> save(HafalanLog e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<HafalanLog>> findById(HafalanLogId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(HafalanLogId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(HafalanLog e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(HafalanLogId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
