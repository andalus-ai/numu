package tech.kayys.numou.project.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;

import java.util.Objects;

/** Cancels a milestone that has not been reached yet. */
public record CancelMilestoneCommand(
        ProjectMilestoneId milestoneId
) implements Command {

    public CancelMilestoneCommand {
        Objects.requireNonNull(milestoneId, "milestoneId cannot be null");
    }
}
