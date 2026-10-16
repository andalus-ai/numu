package tech.kayys.numou.asset.application.inspection;

import tech.kayys.numou.asset.domain.inspection.finding.FindingSeverity;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record AddInspectionFindingCommand(
        String tenantId,
        UUID inspectionId,
        String category,
        String description,
        FindingSeverity severity,
        String recommendedAction,
        UUID workOrderId,
        String recordedBy
) implements Command {

    public AddInspectionFindingCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
    }
}
