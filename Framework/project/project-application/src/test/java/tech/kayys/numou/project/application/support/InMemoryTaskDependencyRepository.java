package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.TaskDependency;
import tech.kayys.syirkah.project.domain.task.TaskDependencyId;
import tech.kayys.syirkah.project.spi.port.TaskDependencyRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link TaskDependencyRepository}. */
public final class InMemoryTaskDependencyRepository
        implements TaskDependencyRepository {

    private final Map<TaskDependencyId, TaskDependency> dependenciesById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<TaskDependency> save(TaskDependency dependency) {
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
        dependenciesById.remove(dependency.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TaskDependencyId id) {
        dependenciesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
