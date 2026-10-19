package tech.kayys.numou.asset.domain.repository;

import tech.kayys.numou.asset.domain.inspection.AssetInspectionId;
import tech.kayys.numou.asset.domain.inspection.finding.InspectionFinding;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped persistence port for inspection findings. */
public interface InspectionFindingRepository {

    CompletionStage<InspectionFinding> save(String tenantId, InspectionFinding finding);

    CompletionStage<List<InspectionFinding>> findByInspection(String tenantId, AssetInspectionId inspectionId);
}
