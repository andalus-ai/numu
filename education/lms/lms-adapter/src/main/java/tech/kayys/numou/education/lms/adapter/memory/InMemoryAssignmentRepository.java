package tech.kayys.numou.education.lms.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.lms.domain.Assignment;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.spi.AssignmentRepository;

public class InMemoryAssignmentRepository implements AssignmentRepository {
    private final Map<AssignmentId, Assignment> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Assignment> save(Assignment e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Assignment>> findById(AssignmentId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(AssignmentId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Assignment e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(AssignmentId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
