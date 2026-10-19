package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.phase.ProjectPhase;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectPhaseRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link ProjectPhaseRepository} adapter. */
public final class InMemoryProjectPhaseRepository
        implements ProjectPhaseRepository {

    private final Map<ProjectPhaseId, ProjectPhase> phasesById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectPhase> save(ProjectPhase phase) {
        Objects.requireNonNull(phase, "phase cannot be null");

        phasesById.put(phase.id(), phase);

        return CompletableFuture.completedFuture(phase);
    }

    @Override
    public CompletionStage<Optional<ProjectPhase>> findById(ProjectPhaseId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(phasesById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectPhaseId id) {
        return CompletableFuture.completedFuture(phasesById.containsKey(id));
    }

    @Override
    public CompletionStage<List<ProjectPhase>> findByProjectId(ProjectId projectId) {
        var found = phasesById.values().stream()
                .filter(phase -> phase.projectId().equals(projectId))
                .sorted(Comparator.comparingInt(ProjectPhase::sequence))
                .toList();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<Void> delete(ProjectPhase phase) {
        Objects.requireNonNull(phase, "phase cannot be null");

        phasesById.remove(phase.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectPhaseId id) {
        phasesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }
}
