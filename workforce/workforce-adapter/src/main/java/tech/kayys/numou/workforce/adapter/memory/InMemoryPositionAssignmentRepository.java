package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.position.PositionAssignment;
import tech.kayys.numou.workforce.domain.position.PositionAssignmentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.PositionAssignmentRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPositionAssignmentRepository implements PositionAssignmentRepository {

    private final Map<PositionAssignmentId, PositionAssignment> assignmentsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<PositionAssignment> save(PositionAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");
        assignmentsById.put(assignment.id(), assignment);
        return CompletableFuture.completedFuture(assignment);
    }

    @Override
    public CompletionStage<Optional<PositionAssignment>> findById(PositionAssignmentId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(assignmentsById.get(id)));
    }

    @Override
    public CompletionStage<List<PositionAssignment>> findByWorkerId(WorkerId workerId) {
        return CompletableFuture.completedFuture(
                assignmentsById.values().stream()
                        .filter(a -> a.workerId().equals(workerId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<PositionAssignment>> findByPositionId(PositionId positionId) {
        return CompletableFuture.completedFuture(
                assignmentsById.values().stream()
                        .filter(a -> a.positionId().equals(positionId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(PositionAssignmentId id) {
        return CompletableFuture.completedFuture(assignmentsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(PositionAssignment aggregate) {
        Objects.requireNonNull(aggregate, "assignment cannot be null");
        assignmentsById.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(PositionAssignmentId id) {
        assignmentsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
