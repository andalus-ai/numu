package tech.kayys.numou.scheduling.adapter.memory;

import tech.kayys.numou.scheduling.domain.Shift;
import tech.kayys.numou.scheduling.domain.ShiftId;
import tech.kayys.numou.scheduling.spi.ShiftRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryShiftRepository implements ShiftRepository {
    private final Map<ShiftId, Shift> store = new ConcurrentHashMap<>();
    public CompletionStage<Shift> save(Shift e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<Shift>> findById(ShiftId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(ShiftId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(Shift e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(ShiftId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
