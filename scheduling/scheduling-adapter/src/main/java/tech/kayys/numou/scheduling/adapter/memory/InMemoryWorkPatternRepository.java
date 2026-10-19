package tech.kayys.numou.scheduling.adapter.memory;

import tech.kayys.numou.scheduling.domain.WorkPattern;
import tech.kayys.numou.scheduling.domain.WorkPatternId;
import tech.kayys.numou.scheduling.spi.WorkPatternRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWorkPatternRepository implements WorkPatternRepository {
    private final Map<WorkPatternId, WorkPattern> store = new ConcurrentHashMap<>();
    public CompletionStage<WorkPattern> save(WorkPattern e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<WorkPattern>> findById(WorkPatternId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(WorkPatternId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(WorkPattern e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(WorkPatternId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
