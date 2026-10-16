package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;
import tech.kayys.syirkah.project.domain.risk.RiskId;

import java.util.Objects;
import java.util.UUID;

/**
 * Raises an issue directly. {@code sourceRiskId} may be null; issues
 * created by risk materialization arrive through the policy instead.
 */
public record RaiseIssueCommand(
        ProjectId projectId,
        RiskId sourceRiskId,
        String number,
        String title,
        String description,
        IssueSeverity severity,
        IssuePriority priority,
        UUID ownerId
) implements Command {

    public RaiseIssueCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }
}
