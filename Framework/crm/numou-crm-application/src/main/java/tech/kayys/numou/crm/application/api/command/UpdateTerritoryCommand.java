package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to update a territory's name or description.
 */
public record UpdateTerritoryCommand(
        TerritoryId territoryId,
        String name,
        String description
) implements Command {

    public UpdateTerritoryCommand {
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        // At least one of name or description should be provided for an update
        if ((name == null || name.isBlank()) && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("Either name or description must be provided");
        }
        // If provided, name cannot be blank
        if (name != null && name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }
    }
}