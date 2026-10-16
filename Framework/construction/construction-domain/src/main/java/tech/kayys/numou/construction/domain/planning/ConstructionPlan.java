package tech.kayys.syirkah.construction.domain.planning;

import tech.kayys.syirkah.construction.domain.planning.event.ConstructionPlanCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionPlan extends AbstractAggregateRoot<ConstructionPlanId> {
    private final UUID projectId;
    private String planName;
    private ConstructionPlanStatus status;

    private ConstructionPlan(ConstructionPlanId id, UUID projectId, String planName) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.planName = Objects.requireNonNull(planName);
        this.status = ConstructionPlanStatus.DRAFT;
    }

    public static ConstructionPlan create(UUID projectId, String planName) {
        var plan = new ConstructionPlan(ConstructionPlanId.generate(), projectId, planName);
        plan.raise(new ConstructionPlanCreated(UUID.randomUUID(), Instant.now(), plan.id().value(), projectId, planName));
        return plan;
    }

    public void activate() {
        this.status = ConstructionPlanStatus.ACTIVE;
    }

    public UUID projectId() { return projectId; }
    public String planName() { return planName; }
    public ConstructionPlanStatus status() { return status; }
}
