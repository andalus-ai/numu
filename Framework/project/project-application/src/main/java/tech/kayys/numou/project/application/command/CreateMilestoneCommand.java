package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.milestone.MilestoneType;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Adds a significant point in time to an existing project.
 *
 * As with phases, the handler verifies the project before the
 * milestone is created.
 */
public record CreateMilestoneCommand(
        ProjectId projectId,
        int sequence,
        String name,
        MilestoneType type,
        LocalDate plannedDate
) implements Command {

    public CreateMilestoneCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(plannedDate, "plannedDate cannot be null");
    }
}
