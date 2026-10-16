package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;

import java.time.LocalDate;
import java.util.Objects;

/** Marks a PLANNED milestone as reached on the given date. */
public record ReachMilestoneCommand(
        ProjectMilestoneId milestoneId,
        LocalDate actualDate
) implements Command {

    public ReachMilestoneCommand {
        Objects.requireNonNull(milestoneId, "milestoneId cannot be null");
        Objects.requireNonNull(actualDate, "actualDate cannot be null");
    }
}
