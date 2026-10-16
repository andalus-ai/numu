package tech.kayys.syirkah.construction.domain.controls;

import tech.kayys.syirkah.construction.domain.controls.event.BaselineApproved;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ProjectControlBaseline extends AbstractAggregateRoot<ProjectControlBaselineId> {
    private final UUID projectId;
    private final String baselineName;
    private ProjectControlBaselineStatus status;

    private ProjectControlBaseline(ProjectControlBaselineId id, UUID projectId, String baselineName) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.baselineName = Objects.requireNonNull(baselineName);
        this.status = ProjectControlBaselineStatus.DRAFT;
    }

    public static ProjectControlBaseline create(UUID projectId, String baselineName) {
        return new ProjectControlBaseline(ProjectControlBaselineId.generate(), projectId, baselineName);
    }

    public void approve() {
        this.status = ProjectControlBaselineStatus.APPROVED;
        raise(new BaselineApproved(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public UUID projectId() { return projectId; }
    public String baselineName() { return baselineName; }
    public ProjectControlBaselineStatus status() { return status; }
}
