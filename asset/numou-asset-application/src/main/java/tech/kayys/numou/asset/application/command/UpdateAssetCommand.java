package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

/** Renames and/or re-types an asset (ASSET-07). */
public record UpdateAssetCommand(String tenantId, AssetId assetId, String name, AssetType type) implements Command {
    public UpdateAssetCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
    }
}
