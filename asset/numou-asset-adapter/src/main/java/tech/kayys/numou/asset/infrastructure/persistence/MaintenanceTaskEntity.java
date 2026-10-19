package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.maintenance.task.MaintenanceTaskStatus;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** A maintenance task belonging to a work order (ASSET-19 §19.12). */
@Entity
@Table(name = "maintenance_task")
public class MaintenanceTaskEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "work_order_id", nullable = false)
    public UUID workOrderId;

    @Column(name = "task_number", nullable = false, length = 100)
    public String taskNumber;

    @Column(name = "title", nullable = false, length = 255)
    public String title;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "sequence", nullable = false)
    public int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    public MaintenanceTaskStatus status;

    @Column(name = "completed_at")
    public Instant completedAt;
}