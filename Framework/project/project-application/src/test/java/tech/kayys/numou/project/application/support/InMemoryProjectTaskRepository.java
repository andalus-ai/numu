package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTask;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProjectTaskRepository}. */
public final class InMemoryProjectTaskRepository
        implements ProjectTaskRepository {

    private final Map<ProjectTaskId, ProjectTask> tasksById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<ProjectTask> save(ProjectTask task) {
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
    public CompletionStage<List<ProjectTask>> findByProjectId(ProjectId projectId) {
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
        tasksById.remove(task.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectTaskId id) {
        tasksById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
