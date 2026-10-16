package tech.kayys.syirkah.asset.domain.inspection.finding;

import tech.kayys.syirkah.asset.domain.inspection.AssetInspectionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A specific issue discovered during an inspection (see ASSET-20
 * sections 20.14-20.15).
 *
 * <p>A finding is a recorded observation: it retains its original
 * severity and description. Corrective work belongs to Maintenance
 * (a future WorkOrder), referenced only by the optional
 * {@code workOrderId}.</p>
 */
public record InspectionFinding(
        InspectionFindingId id,
        AssetInspectionId inspectionId,
        String tenantId,
        UUID assetId,
        String category,
        String description,
        FindingSeverity severity,
        String recommendedAction,
        UUID workOrderId,
        Instant recordedAt,
        String recordedBy
) {

    public InspectionFinding {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(recordedAt, "recordedAt cannot be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description cannot be blank");
        }
    }

    public static InspectionFinding record(
            AssetInspectionId inspectionId, String tenantId, UUID assetId,
            String category, String description, FindingSeverity severity,
            String recommendedAction, UUID workOrderId, Instant recordedAt, String recordedBy) {
        return new InspectionFinding(InspectionFindingId.generate(), inspectionId, tenantId, assetId,
                category, description, severity, recommendedAction, workOrderId, recordedAt, recordedBy);
    }
}
