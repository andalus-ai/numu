package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.event.SuccessionPlanActivated;
import tech.kayys.numou.workforce.domain.talent.event.SuccessionPlanCreated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class SuccessionPlan extends AbstractAggregateRoot<SuccessionPlanId> {

    private final TenantId tenantId;
    private final CriticalPositionId criticalPositionId;
    private String name;
    private String description;
    private SuccessionPlanStatus status;
    private LocalDate reviewDate;

    private SuccessionPlan(
            SuccessionPlanId id,
            TenantId tenantId,
            CriticalPositionId criticalPositionId,
            String name,
            String description,
            LocalDate reviewDate
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.criticalPositionId = Objects.requireNonNull(criticalPositionId, "criticalPositionId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.reviewDate = reviewDate;
        this.status = SuccessionPlanStatus.DRAFT;
    }

    public static SuccessionPlan create(
            SuccessionPlanId id,
            TenantId tenantId,
            CriticalPositionId criticalPositionId,
            String name,
            String description,
            LocalDate reviewDate
    ) {
        SuccessionPlan plan = new SuccessionPlan(id, tenantId, criticalPositionId, name, description, reviewDate);
        plan.raise(new SuccessionPlanCreated(id, tenantId, criticalPositionId));
        return plan;
    }

    public void activate() {
        if (status != SuccessionPlanStatus.DRAFT && status != SuccessionPlanStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Cannot activate plan from status: " + status);
        }
        this.status = SuccessionPlanStatus.ACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new SuccessionPlanActivated(getId()));
    }

    public void startReview() {
        this.status = SuccessionPlanStatus.UNDER_REVIEW;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = SuccessionPlanStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = SuccessionPlanStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public CriticalPositionId getCriticalPositionId() { return criticalPositionId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public SuccessionPlanStatus getStatus() { return status; }
    public LocalDate getReviewDate() { return reviewDate; }
}
