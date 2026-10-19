package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.task.ProjectTaskId;
import tech.kayys.numou.project.domain.task.TaskAssignment;
import tech.kayys.numou.project.domain.task.TaskAssignmentId;
import tech.kayys.numou.project.spi.port.TaskAssignmentRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link TaskAssignmentRepository} adapter. */
public final class InMemoryTaskAssignmentRepository
        implements TaskAssignmentRepository {

    private final Map<TaskAssignmentId, TaskAssignment> assignmentsById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<TaskAssignment> save(TaskAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");

        assignmentsById.put(assignment.id(), assignment);

        return CompletableFuture.completedFuture(assignment);
    }

    @Override
    public CompletionStage<Optional<TaskAssignment>> findById(TaskAssignmentId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(assignmentsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(TaskAssignmentId id) {
        return CompletableFuture.completedFuture(assignmentsById.containsKey(id));
    }

    @Override
    public CompletionStage<List<TaskAssignment>> findByTaskId(
            ProjectTaskId taskId
    ) {
        var found = assignmentsById.values().stream()
                .filter(assignment -> assignment.taskId().equals(taskId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(TaskAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment cannot be null");

        assignmentsById.remove(assignment.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TaskAssignmentId id) {
        assignmentsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
