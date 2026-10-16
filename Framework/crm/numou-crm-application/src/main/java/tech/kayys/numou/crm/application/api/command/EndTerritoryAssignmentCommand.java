package tech.kayys.numou.crm.application.api.command;

import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentId;
import tech.kayys.numou.foundation.application.command.Command;
import java.time.Instant;
import java.util.Objects;

/**
 * Command to end a territory assignment.
 */
public record EndTerritoryAssignmentCommand(
        TerritoryAssignmentId assignmentId,
        Instant endedAt
) implements Command {

    public EndTerritoryAssignmentCommand {
        Objects.requireNonNull(assignmentId, "assignmentId cannot be null");
        Objects.requireNonNull(endedAt, "endedAt cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TerritoryAssignmentId assignmentId;
        private Instant endedAt;

        public Builder assignmentId(TerritoryAssignmentId assignmentId) {
            this.assignmentId = assignmentId;
            return this;
        }

        public Builder endedAt(Instant endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public EndTerritoryAssignmentCommand build() {
            if (endedAt == null) {
                endedAt = Instant.now();
            }
            return new EndTerritoryAssignmentCommand(assignmentId, endedAt);
        }
    }
}