package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;
import java.util.List;
import java.util.UUID;

/** Tenant-explicit persistence boundary for audited measurement corrections. */
public interface QuantityCorrectionRepository {
    /** Returns false for an identical idempotent replay; rejects conflicting key reuse. */
    boolean appendCorrection(QuantityMeasurementCorrection correction);
    List<QuantityMeasurementCorrection> findCorrections(UUID tenantId, UUID definitionId, UUID originalMeasurementId);
    List<QuantityMeasurementCorrection> findCorrectionsForDefinition(UUID tenantId, UUID definitionId);
}
