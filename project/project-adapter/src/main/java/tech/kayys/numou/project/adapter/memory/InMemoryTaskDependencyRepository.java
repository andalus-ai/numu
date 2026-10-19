package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.task.TaskDependency;
import tech.kayys.numou.project.domain.task.TaskDependencyId;
import tech.kayys.numou.project.spi.port.TaskDependencyRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link TaskDependencyRepository} adapter. */
public final class InMemoryTaskDependencyRepository
        implements TaskDependencyRepository {

    private final Map<TaskDependencyId, TaskDependency> dependenciesById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<TaskDependency> save(TaskDependency dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null");

        dependenciesById.put(dependency.id(), dependency);

        return CompletableFuture.completedFuture(dependency);
    }

    @Override
    public CompletionStage<Optional<TaskDependency>> findById(TaskDependencyId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(dependenciesById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(TaskDependencyId id) {
        return CompletableFuture.completedFuture(dependenciesById.containsKey(id));
    }

    @Override
    public CompletionStage<List<TaskDependency>> findByProjectId(ProjectId projectId) {
        var found = dependenciesById.values().stream()
                .filter(dependency -> dependency.projectId().equals(projectId))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(TaskDependency dependency) {
        Objects.requireNonNull(dependency, "dependency cannot be null");

        dependenciesById.remove(dependency.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TaskDependencyId id) {
        dependenciesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
