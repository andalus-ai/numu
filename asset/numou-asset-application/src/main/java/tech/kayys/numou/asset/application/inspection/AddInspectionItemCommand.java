package tech.kayys.numou.asset.application.inspection;

import tech.kayys.numou.asset.domain.inspection.AssetCondition;
import tech.kayys.numou.asset.domain.inspection.InspectionResult;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record AddInspectionItemCommand(
        String tenantId,
        UUID inspectionId,
        String component,
        String description,
        AssetCondition condition,
        InspectionResult result,
        String notes
) implements Command {

    public AddInspectionItemCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
        Objects.requireNonNull(component, "component cannot be null");
    }
}
