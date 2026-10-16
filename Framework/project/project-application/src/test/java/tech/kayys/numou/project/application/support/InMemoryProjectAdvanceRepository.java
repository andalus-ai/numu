package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.commercial.ProjectAdvance;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ProjectAdvanceRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InMemoryProjectAdvanceRepository implements ProjectAdvanceRepository {

    private final Map<ProjectAdvanceId, ProjectAdvance> advancesById = new LinkedHashMap<>();

    @Override
    public CompletionStage<ProjectAdvance> save(ProjectAdvance advance) {
        advancesById.put(advance.id(), advance);
        return CompletableFuture.completedFuture(advance);
    }

    @Override
    public CompletionStage<Optional<ProjectAdvance>> findById(ProjectAdvanceId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(advancesById.get(id)));
    }

    @Override
    public CompletionStage<List<ProjectAdvance>> findByProjectId(ProjectId projectId) {
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
        advancesById.remove(advance.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectAdvanceId id) {
        advancesById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
