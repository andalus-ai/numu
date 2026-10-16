package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record ChangeAssetLocationCommand(String tenantId, AssetId assetId, String locationId, String locationName) implements Command {
    public ChangeAssetLocationCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(locationId, "locationId cannot be null");
        Objects.requireNonNull(locationName, "locationName cannot be null");
    }
}
