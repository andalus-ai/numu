package tech.kayys.syirkah.project.application.risk.policy;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;

import java.util.Objects;
import java.util.UUID;

/**
 * Everything needed to raise an issue from a materialized risk,
 * decided by policy rather than hard-coded in the aggregate.
 *
 * {@code ownerId} may be null — assigning an owner is a follow-up
 * decision, not a precondition for tracking the problem.
 */
public record IssueDraft(
        ProjectId projectId,
        String number,
        String title,
        String description,
        IssueSeverity severity,
        IssuePriority priority,
        UUID ownerId
) {

    public IssueDraft {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }
}
