package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record AssignAssetCommand(String tenantId, AssetId assetId, String partyId, String partyType, String partyName) implements Command {
    public AssignAssetCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(partyId, "partyId cannot be null");
        Objects.requireNonNull(partyType, "partyType cannot be null");
    }
}
