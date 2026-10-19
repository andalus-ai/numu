package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to set a territory's parent.
 */
public record SetTerritoryParentCommand(
        TerritoryId territoryId,
        TerritoryId parentTerritoryId
) implements Command {

    public SetTerritoryParentCommand {
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        // parentTerritoryId can be null (to remove parent)
        // But territory cannot be its own parent
        if (parentTerritoryId != null && parentTerritoryId.equals(territoryId)) {
            throw new IllegalArgumentException("Territory cannot be its own parent");
        }
    }
}