package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.milestone.ProjectMilestone;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectMilestoneRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProjectMilestoneRepository} adapter. */
public final class InMemoryProjectMilestoneRepository
        implements ProjectMilestoneRepository {

    private final Map<ProjectMilestoneId, ProjectMilestone> milestonesById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectMilestone> save(ProjectMilestone milestone) {
        Objects.requireNonNull(milestone, "milestone cannot be null");

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
        Objects.requireNonNull(milestone, "milestone cannot be null");

        milestonesById.remove(milestone.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectMilestoneId id) {
        milestonesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
