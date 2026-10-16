package tech.kayys.numou.asset.application.maintenance.workorder;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;
import java.util.UUID;

public record OpenMaintenanceWorkOrderCommand(String tenantId, UUID workOrderId) implements Command {
    public OpenMaintenanceWorkOrderCommand { Objects.requireNonNull(tenantId); Objects.requireNonNull(workOrderId); }
}
