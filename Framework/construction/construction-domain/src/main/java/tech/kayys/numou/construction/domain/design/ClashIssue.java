package tech.kayys.syirkah.construction.domain.design;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.util.Objects;
import java.util.UUID;

public final class ClashIssue extends AbstractAggregateRoot<ClashIssueId> {
    private final UUID projectId;
    private final String description;
    private final ClashSeverity severity;
    private ClashStatus status;

    private ClashIssue(ClashIssueId id, UUID projectId, String description, ClashSeverity severity) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.description = Objects.requireNonNull(description);
        this.severity = Objects.requireNonNull(severity);
        this.status = ClashStatus.OPEN;
    }

    public static ClashIssue report(UUID projectId, String description, ClashSeverity severity) {
        return new ClashIssue(ClashIssueId.generate(), projectId, description, severity);
    }

    public void resolve() { this.status = ClashStatus.RESOLVED; }

    public UUID projectId() { return projectId; }
    public String description() { return description; }
    public ClashSeverity severity() { return severity; }
    public ClashStatus status() { return status; }
}
