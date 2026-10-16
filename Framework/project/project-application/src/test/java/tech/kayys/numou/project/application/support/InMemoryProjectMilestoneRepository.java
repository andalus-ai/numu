package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.milestone.ProjectMilestone;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ProjectMilestoneRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link ProjectMilestoneRepository}. */
public final class InMemoryProjectMilestoneRepository
        implements ProjectMilestoneRepository {

    private final Map<ProjectMilestoneId, ProjectMilestone> milestonesById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<ProjectMilestone> save(ProjectMilestone milestone) {
        milestonesById.put(milestone.id(), milestone);

        return CompletableFuture.completedFuture(milestone);
    }

    @Override
    public CompletionStage<Optional<ProjectMilestone>> findById(ProjectMilestoneId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(milestonesById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectMilestoneId id) {
        return CompletableFuture.completedFuture(milestonesById.containsKey(id));
    }

    @Override
    public CompletionStage<List<ProjectMilestone>> findByProjectId(ProjectId projectId) {
        var found = milestonesById.values().stream()
                .filter(milestone -> milestone.projectId().equals(projectId))
                .sorted(Comparator.comparingInt(ProjectMilestone::sequence))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(ProjectMilestone milestone) {
        milestonesById.remove(milestone.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectMilestoneId id) {
        milestonesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
