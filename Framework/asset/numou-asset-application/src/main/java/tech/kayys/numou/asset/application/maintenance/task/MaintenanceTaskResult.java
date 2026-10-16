package tech.kayys.numou.asset.application.maintenance.task;

import tech.kayys.numou.asset.domain.maintenance.task.MaintenanceTask;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceTaskResult(UUID id, String tenantId, UUID workOrderId, String taskNumber,
                                     String title, String description, int sequence, String status,
                                     Instant createdAt, Instant completedAt) {
    public static MaintenanceTaskResult from(MaintenanceTask t) {
        return new MaintenanceTaskResult(t.id().value(), t.tenantId(), t.workOrderId(), t.taskNumber(),
                t.title(), t.description(), t.sequence(), t.status().name(), t.createdAt(), t.completedAt());
    }
}
