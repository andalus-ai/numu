package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ProjectAdvance;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectAdvanceRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProjectAdvanceRepository implements ProjectAdvanceRepository {

    private final Map<ProjectAdvanceId, ProjectAdvance> advancesById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectAdvance> save(ProjectAdvance advance) {
        Objects.requireNonNull(advance, "advance cannot be null");
        advancesById.put(advance.id(), advance);
        return CompletableFuture.completedFuture(advance);
    }

    @Override
    public CompletionStage<Optional<ProjectAdvance>> findById(ProjectAdvanceId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(advancesById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ProjectAdvance>> findByProjectId(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

        var list = advancesById.values().stream()
                .filter(a -> a.projectId().equals(projectId))
                .toList();

        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectAdvanceId id) {
        return CompletableFuture.completedFuture(advancesById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ProjectAdvance advance) {
        Objects.requireNonNull(advance, "advance cannot be null");
        advancesById.remove(advance.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectAdvanceId id) {
        advancesById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
