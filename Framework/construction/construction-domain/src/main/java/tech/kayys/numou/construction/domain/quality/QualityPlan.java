package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.construction.domain.quality.event.QualityPlanApproved;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class QualityPlan extends AbstractAggregateRoot<QualityPlanId> {
    private final UUID projectId;
    private String title;
    private QualityPlanStatus status;

    private QualityPlan(QualityPlanId id, UUID projectId, String title) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.title = Objects.requireNonNull(title);
        this.status = QualityPlanStatus.DRAFT;
    }

    public static QualityPlan create(UUID projectId, String title) {
        return new QualityPlan(QualityPlanId.generate(), projectId, title);
    }

    public void approve() {
        this.status = QualityPlanStatus.APPROVED;
        raise(new QualityPlanApproved(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public UUID projectId() { return projectId; }
    public String title() { return title; }
    public QualityPlanStatus status() { return status; }
}
