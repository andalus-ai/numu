package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.QuantityCorrectionDecisionRepository;
import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.AcceptedQuantityProjection;

import java.util.Objects;
import java.util.UUID;

/** Resolves accepted quantity without changing the immutable measurement history. */
public final class GetAcceptedScopeQuantity {
    private final ScopeQuantityRepository quantities;
    private final QuantityCorrectionRepository corrections;
    private final QuantityCorrectionDecisionRepository decisions;

    public GetAcceptedScopeQuantity(ScopeQuantityRepository quantities,
            QuantityCorrectionRepository corrections, QuantityCorrectionDecisionRepository decisions) {
        this.quantities = Objects.requireNonNull(quantities, "Quantity repository cannot be null");
        this.corrections = Objects.requireNonNull(corrections, "Correction repository cannot be null");
        this.decisions = Objects.requireNonNull(decisions, "Decision repository cannot be null");
    }

    public AcceptedQuantityProjection execute(UUID tenantId, UUID definitionId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        var history = quantities.findMeasurementHistory(tenantId, definitionId);
        var correctionHistory = corrections.findCorrectionsForDefinition(tenantId, definitionId);
        var reviewHistory = decisions.findDecisions(tenantId, definitionId);
        return AcceptedQuantityProjection.resolve(tenantId, definitionId, history, correctionHistory, reviewHistory);
    }
}
