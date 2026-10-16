package tech.kayys.numou.asset.infrastructure.persistence.inspection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.inspection.finding.FindingSeverity;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_inspection_finding")
public class AssetInspectionFindingEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "inspection_id", nullable = false)
    public UUID inspectionId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "category", length = 100)
    public String category;

    @Column(name = "description", nullable = false, length = 2000)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    public FindingSeverity severity;

    @Column(name = "recommended_action", length = 2000)
    public String recommendedAction;

    @Column(name = "work_order_id")
    public UUID workOrderId;

    @Column(name = "recorded_at")
    public Instant recordedAt;

    @Column(name = "recorded_by", length = 100)
    public String recordedBy;
}
