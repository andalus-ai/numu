package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskActionStatus;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentAction;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;
import tech.kayys.syirkah.project.spi.port.RiskTreatmentActionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link RiskTreatmentActionRepository}. */
public final class InMemoryRiskTreatmentActionRepository
        implements RiskTreatmentActionRepository {

    private final Map<RiskTreatmentActionId, RiskTreatmentAction> actionsById =
            new LinkedHashMap<>();

    @Override
    public CompletionStage<RiskTreatmentAction> save(RiskTreatmentAction aggregate) {
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

    public RiskTreatmentAction get(RiskTreatmentActionId id) {
        return actionsById.get(id);
    }
}