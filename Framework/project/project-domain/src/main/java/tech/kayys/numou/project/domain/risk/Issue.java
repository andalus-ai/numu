package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.event.IssueClosed;
import tech.kayys.syirkah.project.domain.risk.event.IssueRaised;
import tech.kayys.syirkah.project.domain.risk.event.IssueResolved;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Issue aggregate (P10.10) — a problem that actually happened, as
 * opposed to {@link Risk}, which is a problem that might.
 *
 * Issues are separate aggregates from risks and are created either
 * directly or by the materialization policy when a risk
 * materializes; the Risk aggregate never constructs an Issue itself.
 */
public final class Issue
        extends AbstractAggregateRoot<IssueId> {

    private final ProjectId projectId;

    /** Nullable: an issue may be raised without a source risk. */
    private final RiskId sourceRiskId;

    private final String number;
    private final String title;
    private final String description;

    private final IssueSeverity severity;
    private final IssuePriority priority;

    private IssueStatus status;

    private String rootCause;
    private String resolution;

    private UUID ownerId;

    private Issue(
            IssueId id,
            ProjectId projectId,
            RiskId sourceRiskId,
            String number,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            UUID ownerId
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.sourceRiskId = sourceRiskId;

        this.number = requireText(number, "number");
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");

        this.severity = Objects.requireNonNull(
                severity,
                "severity cannot be null"
        );

        this.priority = Objects.requireNonNull(
                priority,
                "priority cannot be null"
        );

        this.ownerId = ownerId;

        this.status = IssueStatus.OPEN;

        raise(new IssueRaised(
                UUID.randomUUID(),
                Instant.now(),
                id,
                projectId,
                sourceRiskId,
                severity,
                priority
        ));
    }

    public static Issue raise(
            IssueId id,
            ProjectId projectId,
            RiskId sourceRiskId,
            String number,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            UUID ownerId
    ) {
        return new Issue(
                id,
                projectId,
                sourceRiskId,
                number,
                title,
                description,
                severity,
                priority,
                ownerId
        );
    }

    /** Issues born from a materialized risk carry the risk link. */
    public static Issue fromMaterializedRisk(
            IssueId id,
            ProjectId projectId,
            RiskId riskId,
            String number,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            UUID ownerId
    ) {
        return raise(
                id,
                projectId,
                riskId,
                number,
                title,
                description,
                severity,
                priority,
                ownerId
        );
    }

    public void investigate() {
        require(IssueStatus.OPEN);

        this.status = IssueStatus.INVESTIGATING;
    }

    public void planAction() {
        require(IssueStatus.INVESTIGATING);

        this.status = IssueStatus.ACTION_PLANNED;
    }

    public void startWork() {
        require(IssueStatus.ACTION_PLANNED);

        this.status = IssueStatus.IN_PROGRESS;
    }

    public void resolve(String rootCause, String resolution) {
        require(IssueStatus.IN_PROGRESS);

        this.rootCause = requireText(rootCause, "rootCause");
        this.resolution = requireText(resolution, "resolution");
        this.status = IssueStatus.RESOLVED;

        raise(new IssueResolved(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId,
                rootCause
        ));
    }

    public void close() {
        require(IssueStatus.RESOLVED);

        this.status = IssueStatus.CLOSED;

        raise(new IssueClosed(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                projectId
        ));
    }

    public void reject() {
        if (status != IssueStatus.OPEN && status != IssueStatus.INVESTIGATING) {
            throw new InvalidIssueStateException(
                    "Issue cannot be rejected from " + status
            );
        }

        this.status = IssueStatus.REJECTED;
    }

    private void require(IssueStatus expected) {
        if (status != expected) {
            throw new InvalidIssueStateException(
                    "Expected " + expected + " but was " + status
            );
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }

        return value.trim();
    }

    public ProjectId projectId() {
        return projectId;
    }

    public RiskId sourceRiskId() {
        return sourceRiskId;
    }

    public String number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public IssueSeverity severity() {
        return severity;
    }

    public IssuePriority priority() {
        return priority;
    }

    public IssueStatus status() {
        return status;
    }

    public String rootCause() {
        return rootCause;
    }

    public String resolution() {
        return resolution;
    }

    public UUID ownerId() {
        return ownerId;
    }
}