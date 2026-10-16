package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforcePlanApproved;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforcePlanCreated;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class WorkforcePlan extends AbstractAggregateRoot<WorkforcePlanId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private LocalDate planningPeriodStart;
    private LocalDate planningPeriodEnd;
    private WorkforcePlanStatus status;
    private final List<WorkforcePlanItem> items;

    private WorkforcePlan(
            WorkforcePlanId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate planningPeriodStart,
            LocalDate planningPeriodEnd
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.planningPeriodStart = Objects.requireNonNull(planningPeriodStart, "planningPeriodStart must not be null");
        this.planningPeriodEnd = Objects.requireNonNull(planningPeriodEnd, "planningPeriodEnd must not be null");
        this.status = WorkforcePlanStatus.DRAFT;
        this.items = new ArrayList<>();
    }

    public static WorkforcePlan create(
            WorkforcePlanId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate planningPeriodStart,
            LocalDate planningPeriodEnd
    ) {
        WorkforcePlan plan = new WorkforcePlan(id, tenantId, code, name, planningPeriodStart, planningPeriodEnd);
        plan.raise(new WorkforcePlanCreated(id, tenantId, code));
        return plan;
    }

    public void addItem(WorkforcePlanItem item) {
        if (status != WorkforcePlanStatus.DRAFT) {
            throw new IllegalStateException("Items can only be added to draft plans");
        }
        items.add(Objects.requireNonNull(item, "item must not be null"));
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void approve(String approvedBy) {
        if (status != WorkforcePlanStatus.DRAFT) {
            throw new IllegalStateException("Only draft plans can be approved");
        }
        this.status = WorkforcePlanStatus.APPROVED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new WorkforcePlanApproved(getId(), approvedBy));
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getPlanningPeriodStart() { return planningPeriodStart; }
    public LocalDate getPlanningPeriodEnd() { return planningPeriodEnd; }
    public WorkforcePlanStatus getStatus() { return status; }
    public List<WorkforcePlanItem> getItems() { return Collections.unmodifiableList(items); }
}
