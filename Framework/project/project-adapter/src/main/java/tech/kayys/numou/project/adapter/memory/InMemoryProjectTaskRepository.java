package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.ProjectTask;
import tech.kayys.numou.project.domain.task.ProjectTaskId;
import tech.kayys.numou.project.spi.port.ProjectTaskRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProjectTaskRepository} adapter. */
public final class InMemoryProjectTaskRepository
        implements ProjectTaskRepository {

    private final Map<ProjectTaskId, ProjectTask> tasksById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectTask> save(ProjectTask task) {
        Objects.requireNonNull(task, "task cannot be null");

        tasksById.put(task.id(), task);

        return CompletableFuture.completedFuture(task);
    }

    @Override
    public CompletionStage<Optional<ProjectTask>> findById(ProjectTaskId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(tasksById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectTaskId id) {
        return CompletableFuture.completedFuture(tasksById.containsKey(id));
    }

    @Override
    public CompletionStage<List<ProjectTask>> findByProjectId(
            ProjectId projectId
    ) {
        var found = tasksById.values().stream()
                .filter(task -> task.projectId().equals(projectId))
                .sorted(Comparator.comparing(
                        task -> task.taskNumber().value()
                ))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(ProjectTask task) {
        Objects.requireNonNull(task, "task cannot be null");

        tasksById.remove(task.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectTaskId id) {
        tasksById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
