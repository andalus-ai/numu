package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for {@link ProjectRepository}: exercises the handlers
 * without a database. Not a replacement for an integration test
 * against a real persistence adapter.
 */
public final class InMemoryProjectRepository implements ProjectRepository {

    private final Map<ProjectId, Project> projectsById = new LinkedHashMap<>();
    private final Map<ProjectNumber, ProjectId> idsByNumber = new LinkedHashMap<>();

    @Override
    public CompletionStage<Project> save(Project project) {
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
