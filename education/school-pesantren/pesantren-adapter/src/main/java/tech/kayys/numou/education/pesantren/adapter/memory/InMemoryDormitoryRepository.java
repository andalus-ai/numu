package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.Dormitory;
import tech.kayys.numou.education.pesantren.domain.DormitoryId;
import tech.kayys.numou.education.pesantren.spi.DormitoryRepository;

public class InMemoryDormitoryRepository implements DormitoryRepository {
    private final Map<DormitoryId, Dormitory> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Dormitory> save(Dormitory e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Dormitory>> findById(DormitoryId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(DormitoryId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Dormitory e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(DormitoryId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
