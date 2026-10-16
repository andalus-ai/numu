package tech.kayys.numou.asset.application.inspection;

import tech.kayys.numou.asset.domain.inspection.AssetCondition;
import tech.kayys.numou.asset.domain.inspection.InspectionResult;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record CompleteAssetInspectionCommand(
        String tenantId,
        UUID inspectionId,
        AssetCondition condition,
        InspectionResult result
) implements Command {

    public CompleteAssetInspectionCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
    }
}
