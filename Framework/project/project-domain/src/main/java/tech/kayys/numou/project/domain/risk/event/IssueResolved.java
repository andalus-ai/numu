package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.IssueId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IssueResolved(
        UUID eventId,
        Instant occurredAt,
        IssueId issueId,
        ProjectId projectId,
        String rootCause
) implements DomainEvent {

    public IssueResolved {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(issueId, "issueId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(rootCause, "rootCause cannot be null");
    }

    @Override
    public String eventType() {
        return "project.issue-resolved";
    }
}
