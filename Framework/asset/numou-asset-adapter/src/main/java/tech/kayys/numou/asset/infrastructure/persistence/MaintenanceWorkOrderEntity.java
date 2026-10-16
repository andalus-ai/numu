package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenancePriority;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderStatus;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** Maintenance work order (ASSET-19). References the asset by id only. */
@Entity
@Table(name = "maintenance_work_order")
public class MaintenanceWorkOrderEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "work_order_number", nullable = false, length = 100)
    public String workOrderNumber;

    @Column(name = "title", nullable = false, length = 255)
    public String title;

    @Column(name = "description", length = 2000)
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_order_type", nullable = false, length = 40)
    public MaintenanceWorkOrderType workOrderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    public MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    public MaintenanceWorkOrderStatus status;

    @Column(name = "requested_by", length = 100)
    public String requestedBy;

    @Column(name = "assigned_to", length = 100)
    public String assignedTo;

    @Column(name = "opened_at")
    public Instant openedAt;

    @Column(name = "started_at")
    public Instant startedAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "cancelled_at")
    public Instant cancelledAt;
}