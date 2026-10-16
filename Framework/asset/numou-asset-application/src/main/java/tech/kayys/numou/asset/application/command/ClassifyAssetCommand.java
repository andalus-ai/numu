package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record ClassifyAssetCommand(String tenantId, AssetId assetId, String classificationId, String classificationName) implements Command {
    public ClassifyAssetCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(classificationId, "classificationId cannot be null");
        Objects.requireNonNull(classificationName, "classificationName cannot be null");
    }
}
