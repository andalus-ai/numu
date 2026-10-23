package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.QuantityCorrectionDecisionRepository;
import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.domain.QuantityCorrectionDecision;
import java.util.Objects;

/** Records a review outcome only for a correction within the same tenant and definition. */
public final class RecordQuantityCorrectionDecision {
    private final QuantityCorrectionRepository corrections;
    private final QuantityCorrectionDecisionRepository decisions;

    public RecordQuantityCorrectionDecision(QuantityCorrectionRepository corrections,
            QuantityCorrectionDecisionRepository decisions) {
        this.corrections = Objects.requireNonNull(corrections, "Correction repository cannot be null");
        this.decisions = Objects.requireNonNull(decisions, "Decision repository cannot be null");
    }

    public boolean execute(QuantityCorrectionDecision decision) {
        Objects.requireNonNull(decision, "Decision cannot be null");
        boolean exists = corrections.findCorrectionsForDefinition(decision.tenantId(), decision.definitionId())
                .stream().anyMatch(c -> c.id().equals(decision.correctionId()));
        if (!exists) throw new IllegalArgumentException("Correction not found in tenant and definition");
        return decisions.appendDecision(decision);
    }
}
