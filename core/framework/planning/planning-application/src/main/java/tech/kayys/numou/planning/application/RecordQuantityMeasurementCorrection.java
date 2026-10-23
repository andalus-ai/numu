package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;

import java.util.Objects;

/** Records a correction only when both immutable measurements exist in the same tenant and definition. */
public final class RecordQuantityMeasurementCorrection {
    private final ScopeQuantityRepository quantityRepository;
    private final QuantityCorrectionRepository correctionRepository;

    public RecordQuantityMeasurementCorrection(
            ScopeQuantityRepository quantityRepository,
            QuantityCorrectionRepository correctionRepository) {
        this.quantityRepository = Objects.requireNonNull(quantityRepository, "Quantity repository cannot be null");
        this.correctionRepository = Objects.requireNonNull(correctionRepository, "Correction repository cannot be null");
    }

    /** Returns true when recorded; false when the same correction is replayed. */
    public boolean execute(QuantityMeasurementCorrection correction) {
        Objects.requireNonNull(correction, "Correction cannot be null");
        var history = quantityRepository.findMeasurementHistory(correction.tenantId(), correction.definitionId());
        QuantityMeasurement original = history.stream()
                .filter(m -> m.id().equals(correction.originalMeasurementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Original measurement not found in tenant and definition"));
        QuantityMeasurement replacement = history.stream()
                .filter(m -> m.id().equals(correction.replacementMeasurementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Replacement measurement not found in tenant and definition"));
        if (original.id().equals(replacement.id())) {
            throw new IllegalArgumentException("Correction must point to distinct measurements");
        }
        return correctionRepository.appendCorrection(correction);
    }
}
