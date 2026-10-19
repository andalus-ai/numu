package tech.kayys.numou.asset.domain.maintenance.task;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Maintenance task owned by a work order (ASSET-19). Kept as an immutable record with transition factories. */
public record MaintenanceTask(
        MaintenanceTaskId id,
        String tenantId,
        UUID workOrderId,
        String taskNumber,
        String title,
        String description,
        int sequence,
        MaintenanceTaskStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public MaintenanceTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workOrderId, "workOrderId");
        Objects.requireNonNull(taskNumber, "taskNumber");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        if (title.isBlank()) throw new BusinessRuleViolation("title cannot be blank");
    }

    public static MaintenanceTask create(MaintenanceTaskId id, String tenantId, UUID workOrderId,
                                         String taskNumber, String title, String description,
                                         int sequence, Instant now) {
        return new MaintenanceTask(id, tenantId, workOrderId, taskNumber, title, description,
                sequence, MaintenanceTaskStatus.PENDING, now, null);
    }

    public MaintenanceTask start() {
        if (status != MaintenanceTaskStatus.PENDING) {
            throw new InvalidStateException("Task cannot be started from " + status);
        }
        return new MaintenanceTask(id, tenantId, workOrderId, taskNumber, title, description,
                sequence, MaintenanceTaskStatus.IN_PROGRESS, createdAt, null);
    }

    public MaintenanceTask complete(Instant now) {
        if (status == MaintenanceTaskStatus.COMPLETED) {
            throw new InvalidStateException("Completed task cannot be completed again");
        }
        if (status == MaintenanceTaskStatus.SKIPPED) {
            throw new InvalidStateException("Skipped task cannot be completed");
        }
        if (status != MaintenanceTaskStatus.PENDING && status != MaintenanceTaskStatus.IN_PROGRESS) {
            throw new InvalidStateException("Task cannot be completed from " + status);
        }
        return new MaintenanceTask(id, tenantId, workOrderId, taskNumber, title, description,
                sequence, MaintenanceTaskStatus.COMPLETED, createdAt, now);
    }

    public MaintenanceTask skip() {
        if (status == MaintenanceTaskStatus.COMPLETED || status == MaintenanceTaskStatus.SKIPPED) {
            throw new InvalidStateException("Task cannot be skipped from " + status);
        }
        return new MaintenanceTask(id, tenantId, workOrderId, taskNumber, title, description,
                sequence, MaintenanceTaskStatus.SKIPPED, createdAt, null);
    }
}
