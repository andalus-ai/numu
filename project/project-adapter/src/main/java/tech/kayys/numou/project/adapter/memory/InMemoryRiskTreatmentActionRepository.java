package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.RiskActionStatus;
import tech.kayys.numou.project.domain.risk.RiskId;
import tech.kayys.numou.project.domain.risk.RiskTreatmentAction;
import tech.kayys.numou.project.domain.risk.RiskTreatmentActionId;
import tech.kayys.numou.project.spi.port.RiskTreatmentActionRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link RiskTreatmentActionRepository} adapter. */
public final class InMemoryRiskTreatmentActionRepository
        implements RiskTreatmentActionRepository {

    private final Map<RiskTreatmentActionId, RiskTreatmentAction> actionsById =
            new ConcurrentHashMap<>();

    @Override
    public CompletionStage<RiskTreatmentAction> save(RiskTreatmentAction aggregate) {
        Objects.requireNonNull(aggregate, "action cannot be null");

        actionsById.put(aggregate.id(), aggregate);

        return CompletableFuture.completedFuture(aggregate);
    }

    @Override
    public CompletionStage<Optional<RiskTreatmentAction>> findById(
            RiskTreatmentActionId id
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(actionsById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(RiskTreatmentActionId id) {
        return CompletableFuture.completedFuture(actionsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(RiskTreatmentAction aggregate) {
        Objects.requireNonNull(aggregate, "action cannot be null");

        actionsById.remove(aggregate.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(RiskTreatmentActionId id) {
        actionsById.remove(id);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<RiskTreatmentAction>> findByRiskId(RiskId riskId) {
        return CompletableFuture.completedFuture(
                actionsById.values().stream()
                        .filter(action -> action.riskId().equals(riskId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<RiskTreatmentAction>> findOpenByProjectId(
            ProjectId projectId
    ) {
        return CompletableFuture.completedFuture(
                actionsById.values().stream()
                        .filter(action -> action.projectId().equals(projectId))
                        .filter(action -> action.status() == RiskActionStatus.OPEN
                                || action.status() == RiskActionStatus.IN_PROGRESS
                                || action.status() == RiskActionStatus.OVERDUE)
                        .toList()
        );
    }
}
