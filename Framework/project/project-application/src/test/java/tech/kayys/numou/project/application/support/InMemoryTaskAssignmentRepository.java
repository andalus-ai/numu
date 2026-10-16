package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskAssignment;
import tech.kayys.syirkah.project.domain.task.TaskAssignmentId;
import tech.kayys.syirkah.project.spi.port.TaskAssignmentRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link TaskAssignmentRepository}. */
public final class InMemoryTaskAssignmentRepository
        implements TaskAssignmentRepository {

    private final Map<TaskAssignmentId, TaskAssignment> assignmentsById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<TaskAssignment> save(TaskAssignment assignment) {
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
    public CompletionStage<List<TaskAssignment>> findByTaskId(ProjectTaskId taskId) {
        var found = assignmentsById.values().stream()
                .filter(assignment -> assignment.taskId().equals(taskId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(TaskAssignment assignment) {
        assignmentsById.remove(assignment.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TaskAssignmentId id) {
        assignmentsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
