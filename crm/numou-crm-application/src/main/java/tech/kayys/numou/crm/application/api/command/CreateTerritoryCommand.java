package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.foundation.application.command.Command;
import java.util.Objects;

/**
 * Command to create a new territory.
 */
public record CreateTerritoryCommand(
        TerritoryId territoryId,
        String name,
        String description,
        TerritoryId parentTerritoryId
) implements Command {

    public CreateTerritoryCommand {
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        // description can be null (will default to empty string)
        // parentTerritoryId can be null (for top-level territories)
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TerritoryId territoryId;
        private String name;
        private String description;
        private TerritoryId parentTerritoryId;

        public Builder territoryId(TerritoryId territoryId) {
            this.territoryId = territoryId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parentTerritoryId(TerritoryId parentTerritoryId) {
            this.parentTerritoryId = parentTerritoryId;
            return this;
        }

        public CreateTerritoryCommand build() {
            if (territoryId == null) {
                territoryId = TerritoryId.generate();
            }
            return new CreateTerritoryCommand(
                    territoryId, name, description, parentTerritoryId
            );
        }
    }
}