package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.identifier.AccountId;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.crm.domain.territory.TerritoryId;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentOrigin;
import tech.kayys.numou.foundation.application.command.Command;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Command to create a new territory assignment.
 */
public record CreateTerritoryAssignmentCommand(
        TerritoryAssignmentId assignmentId,
        TerritoryId territoryId,
        AccountId accountId,
        TerritoryAssignmentOrigin origin,
        UUID assignedByUserId,
        Instant assignedAt
) implements Command {

    public CreateTerritoryAssignmentCommand {
        Objects.requireNonNull(assignmentId, "assignmentId cannot be null");
        Objects.requireNonNull(territoryId, "territoryId cannot be null");
        Objects.requireNonNull(accountId, "accountId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
        Objects.requireNonNull(assignedByUserId, "assignedByUserId cannot be null");
        Objects.requireNonNull(assignedAt, "assignedAt cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TerritoryAssignmentId assignmentId;
        private TerritoryId territoryId;
        private AccountId accountId;
        private TerritoryAssignmentOrigin origin;
        private UUID assignedByUserId;
        private Instant assignedAt;

        public Builder assignmentId(TerritoryAssignmentId assignmentId) {
            this.assignmentId = assignmentId;
            return this;
        }

        public Builder territoryId(TerritoryId territoryId) {
            this.territoryId = territoryId;
            return this;
        }

        public Builder accountId(AccountId accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder origin(TerritoryAssignmentOrigin origin) {
            this.origin = origin;
            return this;
        }

        public Builder assignedByUserId(UUID assignedByUserId) {
            this.assignedByUserId = assignedByUserId;
            return this;
        }

        public Builder assignedAt(Instant assignedAt) {
            this.assignedAt = assignedAt;
            return this;
        }

        public CreateTerritoryAssignmentCommand build() {
            if (assignmentId == null) {
                assignmentId = TerritoryAssignmentId.generate();
            }
            if (assignedAt == null) {
                assignedAt = Instant.now();
            }
            return new CreateTerritoryAssignmentCommand(
                    assignmentId, territoryId, accountId, origin, assignedByUserId, assignedAt
            );
        }
    }
}