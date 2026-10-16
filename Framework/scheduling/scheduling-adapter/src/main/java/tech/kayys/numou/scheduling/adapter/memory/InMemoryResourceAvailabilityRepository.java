package tech.kayys.numou.scheduling.adapter.memory;

import tech.kayys.numou.scheduling.domain.ResourceAvailability;
import tech.kayys.numou.scheduling.domain.ResourceAvailabilityId;
import tech.kayys.numou.scheduling.spi.ResourceAvailabilityRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryResourceAvailabilityRepository implements ResourceAvailabilityRepository {
    private final Map<ResourceAvailabilityId, ResourceAvailability> store = new ConcurrentHashMap<>();
    public CompletionStage<ResourceAvailability> save(ResourceAvailability e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<ResourceAvailability>> findById(ResourceAvailabilityId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(ResourceAvailabilityId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(ResourceAvailability e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(ResourceAvailabilityId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
