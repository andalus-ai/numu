package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reads correction history only inside an explicitly supplied tenant and definition scope. */
public final class GetQuantityCorrectionHistory {
    private final QuantityCorrectionRepository repository;

    public GetQuantityCorrectionHistory(QuantityCorrectionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Correction repository cannot be null");
    }

    public List<QuantityMeasurementCorrection> execute(
            UUID tenantId, UUID definitionId, UUID originalMeasurementId) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(definitionId, "Definition id cannot be null");
        Objects.requireNonNull(originalMeasurementId, "Original measurement id cannot be null");
        return List.copyOf(repository.findCorrections(tenantId, definitionId, originalMeasurementId));
    }
}
