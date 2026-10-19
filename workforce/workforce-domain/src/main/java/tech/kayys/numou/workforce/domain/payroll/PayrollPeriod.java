package tech.kayys.numou.workforce.domain.payroll;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.event.PayrollPeriodCreated;
import tech.kayys.numou.workforce.domain.payroll.event.PayrollPeriodFinalized;

import java.time.LocalDate;
import java.util.Objects;

/**
 * PayrollPeriod defines the business time window for which payroll is calculated.
 *
 * <p>Distinct from PayrollRun — the period is a business fact, the run is an execution.
 */
public final class PayrollPeriod extends AbstractAggregateRoot<PayrollPeriodId> {

    private final TenantId tenantId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalDate paymentDate;
    private PayrollPeriodStatus status;

    private PayrollPeriod(
            PayrollPeriodId id,
            TenantId tenantId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate paymentDate
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        this.paymentDate = Objects.requireNonNull(paymentDate, "paymentDate must not be null");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
        this.status = PayrollPeriodStatus.OPEN;
    }

    public static PayrollPeriod create(
            PayrollPeriodId id,
            TenantId tenantId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate paymentDate
    ) {
        PayrollPeriod period = new PayrollPeriod(id, tenantId, startDate, endDate, paymentDate);
        period.raise(new PayrollPeriodCreated(id, tenantId, startDate, endDate, paymentDate));
        return period;
    }

    public void startProcessing() {
        if (status != PayrollPeriodStatus.OPEN) {
            throw new IllegalStateException("Payroll period is not open");
        }
        this.status = PayrollPeriodStatus.PROCESSING;
    }

    public void finalizePeriod() {
        if (status != PayrollPeriodStatus.PROCESSING) {
            throw new IllegalStateException("Payroll period is not in processing state");
        }
        this.status = PayrollPeriodStatus.FINALIZED;
        raise(new PayrollPeriodFinalized(getId(), tenantId));
    }

    public void close() {
        if (status != PayrollPeriodStatus.FINALIZED) {
            throw new IllegalStateException("Payroll period must be finalized before closing");
        }
        this.status = PayrollPeriodStatus.CLOSED;
    }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public TenantId tenantId() { return tenantId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public LocalDate paymentDate() { return paymentDate; }
    public PayrollPeriodStatus status() { return status; }
    public boolean isOpen() { return status == PayrollPeriodStatus.OPEN; }
}
