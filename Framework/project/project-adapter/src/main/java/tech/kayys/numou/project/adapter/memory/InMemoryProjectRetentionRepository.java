package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ProjectRetention;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;
import tech.kayys.numou.project.domain.commercial.RetentionStatus;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ProjectRetentionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProjectRetentionRepository implements ProjectRetentionRepository {

    private final Map<ProjectRetentionId, ProjectRetention> retentionsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ProjectRetention> save(ProjectRetention retention) {
        Objects.requireNonNull(retention, "retention cannot be null");
        retentionsById.put(retention.id(), retention);
        return CompletableFuture.completedFuture(retention);
    }

    @Override
    public CompletionStage<Optional<ProjectRetention>> findById(ProjectRetentionId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(retentionsById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ProjectRetention>> findByProjectId(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

        var list = retentionsById.values().stream()
                .filter(r -> r.projectId().equals(projectId))
                .toList();

        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<List<ProjectRetention>> findOpenByProjectId(ProjectId projectId) {
        Objects.requireNonNull(projectId, "projectId cannot be null");

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
        Objects.requireNonNull(retention, "retention cannot be null");
        retentionsById.remove(retention.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ProjectRetentionId id) {
        retentionsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
