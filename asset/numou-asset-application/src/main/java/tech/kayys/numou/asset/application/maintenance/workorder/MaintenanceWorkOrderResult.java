package tech.kayys.numou.asset.application.maintenance.workorder;

import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrder;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderId;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderStatus;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceWorkOrderResult(
        UUID id, String tenantId, UUID assetId, String workOrderNumber, String title,
        String description, String type, String priority, String status,
        String requestedBy, String assignedTo,
        Instant openedAt, Instant startedAt, Instant completedAt, Instant cancelledAt) {
    public static MaintenanceWorkOrderResult from(MaintenanceWorkOrder wo) {
        return new MaintenanceWorkOrderResult(wo.id().value(), wo.tenantId(), wo.assetId(),
                wo.workOrderNumber(), wo.title(), wo.description(),
                wo.type().name(), wo.priority().name(), wo.status().name(),
                wo.requestedBy(), wo.assignedTo(),
                wo.openedAt(), wo.startedAt(), wo.completedAt(), wo.cancelledAt());
    }
}
