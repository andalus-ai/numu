package tech.kayys.numou.workforce.domain.payroll;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.event.PayrollRunApproved;
import tech.kayys.numou.workforce.domain.payroll.event.PayrollRunCreated;

import java.time.Instant;
import java.util.Objects;

/**
 * PayrollRun represents a calculation execution for a PayrollPeriod.
 *
 * <p>A single period may have multiple runs (draft, recalculation, final).
 * Each run produces Payslips for all workers in scope.
 */
public final class PayrollRun extends AbstractAggregateRoot<PayrollRunId> {

    private final PayrollPeriodId periodId;
    private final TenantId tenantId;
    private final Instant createdAt;
    private PayrollRunStatus status;

    private PayrollRun(
            PayrollRunId id,
            PayrollPeriodId periodId,
            TenantId tenantId,
            Instant createdAt
    ) {
        super(id);
        this.periodId = Objects.requireNonNull(periodId, "periodId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = PayrollRunStatus.DRAFT;
    }

    public static PayrollRun create(
            PayrollRunId id,
            PayrollPeriodId periodId,
            TenantId tenantId,
            Instant createdAt
    ) {
        PayrollRun run = new PayrollRun(id, periodId, tenantId, createdAt);
        run.raise(new PayrollRunCreated(id, periodId, tenantId));
        return run;
    }

    public void startCalculation() {
        if (status != PayrollRunStatus.DRAFT) {
            throw new IllegalStateException("Payroll run must be in DRAFT to start calculation");
        }
        this.status = PayrollRunStatus.CALCULATING;
    }

    public void markCalculated() {
        if (status != PayrollRunStatus.CALCULATING) {
            throw new IllegalStateException("Payroll run must be CALCULATING to be marked calculated");
        }
        this.status = PayrollRunStatus.CALCULATED;
    }

    public void approve() {
        if (status != PayrollRunStatus.CALCULATED) {
            throw new IllegalStateException("Payroll run must be CALCULATED to be approved");
        }
        this.status = PayrollRunStatus.APPROVED;
        raise(new PayrollRunApproved(getId(), periodId));
    }

    public void post() {
        if (status != PayrollRunStatus.APPROVED) {
            throw new IllegalStateException("Payroll run must be APPROVED to be posted");
        }
        this.status = PayrollRunStatus.POSTED;
    }

    public void cancel() {
        if (status == PayrollRunStatus.POSTED) {
            throw new IllegalStateException("Cannot cancel a posted payroll run");
        }
        this.status = PayrollRunStatus.CANCELLED;
    }

    public PayrollPeriodId periodId() { return periodId; }
    public TenantId tenantId() { return tenantId; }
    public Instant createdAt() { return createdAt; }
    public PayrollRunStatus status() { return status; }
    public boolean isDraft() { return status == PayrollRunStatus.DRAFT; }
    public boolean isApproved() { return status == PayrollRunStatus.APPROVED; }
    public boolean isPosted() { return status == PayrollRunStatus.POSTED; }
}
