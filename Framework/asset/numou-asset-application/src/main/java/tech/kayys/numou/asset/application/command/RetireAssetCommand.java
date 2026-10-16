package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record RetireAssetCommand(String tenantId, AssetId assetId) implements Command {
    public RetireAssetCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
