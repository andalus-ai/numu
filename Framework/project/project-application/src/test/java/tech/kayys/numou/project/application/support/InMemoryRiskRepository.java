package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.Risk;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskStatus;
import tech.kayys.syirkah.project.spi.port.RiskRepository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link RiskRepository}. */
public final class InMemoryRiskRepository implements RiskRepository {

    private final Map<RiskId, Risk> risksById = new LinkedHashMap<>();
    private final Set<RiskId> closed = new LinkedHashSet<>();

    @Override
    public CompletionStage<Risk> save(Risk aggregate) {
        risksById.put(aggregate.id(), aggregate);

        return CompletableFuture.completedFuture(aggregate);
    }

    @Override
    public CompletionStage<Optional<Risk>> findById(RiskId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(risksById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(RiskId id) {
        return CompletableFuture.completedFuture(risksById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Risk aggregate) {
        risksById.remove(aggregate.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(RiskId id) {
        risksById.remove(id);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<Risk>> findByNumber(
            ProjectId projectId, String number
    ) {
        return CompletableFuture.completedFuture(
                risksById.values().stream()
                        .filter(risk -> risk.projectId().equals(projectId))
                        .filter(risk -> risk.number().equals(number))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<List<Risk>> findOpenByProjectId(ProjectId projectId) {
        return CompletableFuture.completedFuture(
                risksById.values().stream()
                        .filter(risk -> risk.projectId().equals(projectId))
                        .filter(risk -> risk.status() != RiskStatus.CLOSED)
                        .toList()
        );
    }

    /** Exposes every risk for the in-memory read model. */
    public List<Risk> findAllByProjectId(ProjectId projectId) {
        return risksById.values().stream()
                .filter(risk -> risk.projectId().equals(projectId))
                .toList();
    }

    public Risk get(RiskId id) {
        return risksById.get(id);
    }
}