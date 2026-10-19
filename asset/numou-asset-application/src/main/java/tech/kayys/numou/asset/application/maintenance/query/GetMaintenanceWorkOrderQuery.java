package tech.kayys.numou.asset.application.maintenance.query;

import java.util.Objects;
import java.util.UUID;

public record GetMaintenanceWorkOrderQuery(String tenantId, UUID workOrderId) {
    public GetMaintenanceWorkOrderQuery { Objects.requireNonNull(tenantId); Objects.requireNonNull(workOrderId); }
}
