package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to retire a territory.
 */
public record RetireTerritoryCommand(
        TerritoryId territoryId
) implements Command {

    public RetireTerritoryCommand {
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
    }
}