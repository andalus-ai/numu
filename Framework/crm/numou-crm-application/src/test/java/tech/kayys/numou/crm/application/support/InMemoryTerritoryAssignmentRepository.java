package tech.kayys.numou.crm.application.support;

import tech.kayys.numou.crm.domain.territory.TerritoryAssignment;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.domain.repository.TerritoryAssignmentRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * In-memory implementation of {@link TerritoryAssignmentRepository} for testing.
 */
public class InMemoryTerritoryAssignmentRepository implements TerritoryAssignmentRepository {

    private final Map<TerritoryAssignmentId, TerritoryAssignment> store = new HashMap<>();

    @Override
    public CompletionStage<TerritoryAssignment> save(TerritoryAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");
        store.put(assignment.id(), assignment);
        return CompletableFuture.completedFuture(assignment);
    }

    @Override
    public CompletionStage<Optional<TerritoryAssignment>> findById(TerritoryAssignmentId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(TerritoryAssignmentId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(TerritoryAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");
        store.remove(assignment.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TerritoryAssignmentId id) {
        Objects.requireNonNull(id, "id cannot be null");
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}