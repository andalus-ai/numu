package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforceDemandApproved;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforceDemandCreated;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class WorkforceDemand extends AbstractAggregateRoot<WorkforceDemandId> {

    private final TenantId tenantId;
    private final PositionId positionId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int quantity;
    private String reason;
    private DemandStatus status;

    private WorkforceDemand(
            WorkforceDemandId id,
            TenantId tenantId,
            PositionId positionId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int quantity,
            String reason
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.positionId = Objects.requireNonNull(positionId, "positionId must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        this.quantity = quantity;
        this.reason = reason;
        this.status = DemandStatus.DRAFT;
    }

    public static WorkforceDemand create(
            WorkforceDemandId id,
            TenantId tenantId,
            PositionId positionId,
            LocalDate periodStart,
            LocalDate periodEnd,
            int quantity,
            String reason
    ) {
        WorkforceDemand demand = new WorkforceDemand(id, tenantId, positionId, periodStart, periodEnd, quantity, reason);
        demand.raise(new WorkforceDemandCreated(id, tenantId, positionId, quantity));
        return demand;
    }

    public void propose() {
        this.status = DemandStatus.PROPOSED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void approve(String approvedBy) {
        this.status = DemandStatus.APPROVED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new WorkforceDemandApproved(getId(), approvedBy));
    }

    public void fulfill() {
        this.status = DemandStatus.FULFILLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = DemandStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public PositionId getPositionId() { return positionId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public DemandStatus getStatus() { return status; }
}
