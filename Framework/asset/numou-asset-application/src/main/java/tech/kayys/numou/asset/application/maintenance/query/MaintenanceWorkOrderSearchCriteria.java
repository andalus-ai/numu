package tech.kayys.numou.asset.application.maintenance.query;

import tech.kayys.numou.asset.application.maintenance.workorder.MaintenanceWorkOrderResult;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderStatus;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderType;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceWorkOrderSearchCriteria(
        String tenantId, UUID assetId, MaintenanceWorkOrderStatus status,
        MaintenanceWorkOrderType type, int page, int size) {
    public MaintenanceWorkOrderSearchCriteria {
        Objects.requireNonNull(tenantId);
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, 200);
    }
    public static MaintenanceWorkOrderSearchCriteria of(String tenantId) {
        return new MaintenanceWorkOrderSearchCriteria(tenantId, null, null, null, 0, 20);
    }
}
