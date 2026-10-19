package tech.kayys.numou.asset.application.inspection;

import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;
import java.util.UUID;

public record CancelAssetInspectionCommand(String tenantId, UUID inspectionId) implements Command {
    public CancelAssetInspectionCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(inspectionId, "inspectionId cannot be null");
    }
}
