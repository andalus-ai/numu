package tech.kayys.numou.crm.application.support;

import tech.kayys.numou.crm.domain.territory.Territory;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.domain.repository.TerritoryRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * In-memory implementation of {@link TerritoryRepository} for testing.
 */
public class InMemoryTerritoryRepository implements TerritoryRepository {

    private final Map<TerritoryId, Territory> store = new HashMap<>();

    @Override
    public CompletionStage<Territory> save(Territory territory) {
        Objects.requireNonNull(territory, "territory cannot be null");
        store.put(territory.id(), territory);
        return CompletableFuture.completedFuture(territory);
    }

    @Override
    public CompletionStage<Optional<Territory>> findById(TerritoryId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Territory territory) {
        Objects.requireNonNull(territory, "territory cannot be null");
        store.remove(territory.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryId id) {
        Objects.requireNonNull(id, "id cannot be null");
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}