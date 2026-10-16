package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;
import tech.kayys.syirkah.project.domain.risk.RiskId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code sourceRiskId} is nullable: issues may be raised directly, not
 * only from a materialized risk.
 */
public record IssueRaised(
        UUID eventId,
        Instant occurredAt,
        IssueId issueId,
        ProjectId projectId,
        RiskId sourceRiskId,
        IssueSeverity severity,
        IssuePriority priority
) implements DomainEvent {

    public IssueRaised {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(issueId, "issueId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
    }

    @Override
    public String eventType() {
        return "project.issue-raised";
    }
}
