package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.commercial.ProjectRetention;
import tech.kayys.syirkah.project.domain.commercial.ProjectRetentionId;
import tech.kayys.syirkah.project.domain.commercial.RetentionStatus;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ProjectRetentionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InMemoryProjectRetentionRepository implements ProjectRetentionRepository {

    private final Map<ProjectRetentionId, ProjectRetention> retentionsById = new LinkedHashMap<>();

    @Override
    public CompletionStage<ProjectRetention> save(ProjectRetention retention) {
        retentionsById.put(retention.id(), retention);
        return CompletableFuture.completedFuture(retention);
    }

    @Override
    public CompletionStage<Optional<ProjectRetention>> findById(ProjectRetentionId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(retentionsById.get(id)));
    }

    @Override
    public CompletionStage<List<ProjectRetention>> findByProjectId(ProjectId projectId) {
        var list = retentionsById.values().stream()
                .filter(r -> r.projectId().equals(projectId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<List<ProjectRetention>> findOpenByProjectId(ProjectId projectId) {
        var list = retentionsById.values().stream()
                .filter(r -> r.projectId().equals(projectId)
                        && (r.status() == RetentionStatus.HELD || r.status() == RetentionStatus.PARTIALLY_RELEASED))
                .toList();
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ProjectRetentionId id) {
        return CompletableFuture.completedFuture(retentionsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ProjectRetention retention) {
        retentionsById.remove(retention.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectRetentionId id) {
        retentionsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
