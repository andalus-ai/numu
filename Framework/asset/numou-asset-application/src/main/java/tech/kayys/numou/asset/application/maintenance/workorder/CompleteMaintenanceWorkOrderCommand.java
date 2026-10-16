package tech.kayys.numou.asset.application.maintenance.workorder;

import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;
import java.util.UUID;

public record CompleteMaintenanceWorkOrderCommand(String tenantId, UUID workOrderId) implements Command {
    public CompleteMaintenanceWorkOrderCommand { Objects.requireNonNull(tenantId); Objects.requireNonNull(workOrderId); }
}
