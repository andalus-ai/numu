package tech.kayys.numou.asset.application.command;

import tech.kayys.numou.asset.domain.relationship.AssetRelationshipId;
import tech.kayys.numou.foundation.application.command.Command;

import java.util.Objects;

public record RemoveAssetRelationshipCommand(String tenantId, AssetRelationshipId relationshipId) implements Command {
    public RemoveAssetRelationshipCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(relationshipId, "relationshipId cannot be null");
    }
}
