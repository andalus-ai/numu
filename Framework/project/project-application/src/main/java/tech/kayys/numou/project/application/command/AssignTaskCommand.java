package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.ResourceType;

import java.util.Objects;
import java.util.UUID;

/**
 * Assigns a resource to a task.
 *
 * The resource is deliberately a type/id pair rather than a
 * concrete Employee/Team/Asset — tasks can be assigned across
 * bounded contexts without the Project aggregate knowing them.
 */
public record AssignTaskCommand(
        ProjectId projectId,
        ProjectTaskId taskId,
        ResourceType resourceType,
        UUID resourceId,
        String role,
        Double plannedHours
) implements Command {

    public AssignTaskCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        Objects.requireNonNull(resourceId, "resourceId cannot be null");
    }
}
