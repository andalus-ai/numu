package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record UnassignAssetCommand(String tenantId, AssetId assetId) implements Command {
    public UnassignAssetCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
