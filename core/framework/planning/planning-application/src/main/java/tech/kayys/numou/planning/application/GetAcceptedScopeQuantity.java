package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.QuantityCorrectionDecisionRepository;
import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.AcceptedQuantityProjection;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;
import tech.kayys.numou.planning.domain.QuantityCorrectionDecision;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Resolves accepted quantity without changing immutable measurement history. */
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

    /** Resolves the latest projection using all recorded evidence. */
    public AcceptedQuantityProjection execute(UUID tenantId, UUID definitionId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        return AcceptedQuantityProjection.resolve(tenantId, definitionId,
                quantities.findMeasurementHistory(tenantId, definitionId),
                corrections.findCorrectionsForDefinition(tenantId, definitionId),
                decisions.findDecisions(tenantId, definitionId));
    }

    /**
     * Resolves a historical snapshot. Measurement dates and correction/review event dates
     * are interpreted against UTC calendar dates to keep results deterministic across hosts.
     */
    public AcceptedQuantityProjection execute(UUID tenantId, UUID definitionId, LocalDate asOfDate) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");

        var measurementHistory = quantities.findMeasurementHistory(tenantId, definitionId).stream()
                .filter(m -> !m.asOfDate().isAfter(asOfDate)).toList();
        Set<UUID> measurementIds = measurementHistory.stream().map(QuantityMeasurement::id).collect(Collectors.toSet());
        var correctionHistory = corrections.findCorrectionsForDefinition(tenantId, definitionId).stream()
                .filter(c -> !c.recordedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(asOfDate))
                .filter(c -> measurementIds.contains(c.originalMeasurementId()) && measurementIds.contains(c.replacementMeasurementId()))
                .toList();
        Set<UUID> correctionIds = correctionHistory.stream().map(QuantityMeasurementCorrection::id).collect(Collectors.toSet());
        var reviewHistory = decisions.findDecisions(tenantId, definitionId).stream()
                .filter(d -> !d.decidedAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(asOfDate))
                .filter(d -> correctionIds.contains(d.correctionId()))
                .toList();
        return AcceptedQuantityProjection.resolve(tenantId, definitionId, measurementHistory, correctionHistory, reviewHistory);
    }
}
