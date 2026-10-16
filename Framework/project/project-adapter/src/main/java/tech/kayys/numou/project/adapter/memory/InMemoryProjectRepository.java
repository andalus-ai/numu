package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.Project;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.project.ProjectNumber;
import tech.kayys.numou.project.spi.port.ProjectRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ProjectRepository} adapter.
 *
 * Good enough for tests and local runs; a JPA/Postgres adapter would
 * replace it without any change to the domain or application layers.
 * The business-key index mirrors the tenant-scoped unique constraint on
 * (tenant_id, project_number) from project01.md section 15.
 */
public final class InMemoryProjectRepository implements ProjectRepository {

    private final Map<ProjectId, Project> projectsById = new ConcurrentHashMap<>();
    private final Map<ProjectNumber, ProjectId> idsByNumber = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Project> save(Project project) {
        Objects.requireNonNull(project, "project cannot be null");

        projectsById.put(project.id(), project);
        idsByNumber.put(project.projectNumber(), project.id());

        return CompletableFuture.completedFuture(project);
    }

    @Override
    public CompletionStage<Optional<Project>> findById(ProjectId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(projectsById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<Project>> findByNumber(ProjectNumber number) {
        var id = idsByNumber.get(number);

        return CompletableFuture.completedFuture(
                id == null
                        ? Optional.empty()
                        : Optional.ofNullable(projectsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectId id) {
        return CompletableFuture.completedFuture(projectsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Project project) {
        Objects.requireNonNull(project, "project cannot be null");

        projectsById.remove(project.id());
        idsByNumber.remove(project.projectNumber());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectId id) {
        var removed = projectsById.remove(id);

        if (removed != null) {
            idsByNumber.remove(removed.projectNumber());
        }

        return CompletableFuture.completedFuture(null);
    }
}
