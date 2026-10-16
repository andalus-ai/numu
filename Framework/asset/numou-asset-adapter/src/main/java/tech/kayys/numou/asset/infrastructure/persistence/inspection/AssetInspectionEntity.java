package tech.kayys.numou.asset.infrastructure.persistence.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.inspection.AssetCondition;
import tech.kayys.numou.asset.domain.inspection.AssetInspectionStatus;
import tech.kayys.numou.asset.domain.inspection.InspectionResult;
import tech.kayys.numou.asset.domain.inspection.InspectionType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_inspection")
public class AssetInspectionEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "inspection_number", nullable = false, length = 100)
    public String inspectionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 50)
    public InspectionType inspectionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    public AssetInspectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_condition", length = 50)
    public AssetCondition overallCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 50)
    public InspectionResult result;

    @Column(name = "inspector_id", length = 100)
    public String inspectorId;

    @Column(name = "notes", columnDefinition = "text")
    public String notes;

    @Column(name = "work_order_id")
    public UUID workOrderId;

    @Column(name = "scheduled_for")
    public Instant scheduledFor;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "cancelled_at")
    public Instant cancelledAt;
}
