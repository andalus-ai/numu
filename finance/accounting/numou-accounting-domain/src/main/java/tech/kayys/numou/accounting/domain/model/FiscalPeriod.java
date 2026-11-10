package tech.kayys.numou.accounting.domain.model;

import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.valueobject.ValueObject;

import java.time.LocalDate;
import java.util.Objects;

public final class FiscalPeriod extends AbstractAggregateRoot<FiscalPeriodId> {
    public enum PeriodStatus implements ValueObject { OPEN, CLOSED, LOCKED }

    private final FiscalPeriodId id;
    private final TenantRef tenantId;
    private final LedgerId ledgerId;
    private final int periodNumber;
    private final String periodName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private PeriodStatus status;

    public FiscalPeriod(FiscalPeriodId id, TenantRef tenantId, LedgerId ledgerId, int periodNumber, String periodName, LocalDate startDate, LocalDate endDate) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.periodNumber = periodNumber;
        this.periodName = Objects.requireNonNull(periodName);
        this.startDate = Objects.requireNonNull(startDate);
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("endDate cannot be before startDate");
        this.endDate = Objects.requireNonNull(endDate);
        this.status = PeriodStatus.OPEN;
    }

    /** Transitional compatibility constructor; new periods must provide explicit ownership. */
    @Deprecated(forRemoval = false)
    public FiscalPeriod(FiscalPeriodId id, int periodNumber, String periodName, LocalDate startDate, LocalDate endDate) {
        this(id, TenantRef.defaultTenant(), LedgerId.primary(), periodNumber, periodName, startDate, endDate);
    }

    public TenantRef getTenantId() { return tenantId; }
    public LedgerId getLedgerId() { return ledgerId; }

    @Override
    public FiscalPeriodId id() { return id; }
    public FiscalPeriodId getId() { return id; }
    public int getPeriodNumber() { return periodNumber; }
    public String getPeriodName() { return periodName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public PeriodStatus getStatus() { return status; }

    public boolean isOpen() { return status == PeriodStatus.OPEN; }
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public void close() {
        if (this.status == PeriodStatus.LOCKED) {
            throw new IllegalStateException("Cannot close locked period");
        }
        this.status = PeriodStatus.CLOSED;
    }

    public void lock() {
        this.status = PeriodStatus.LOCKED;
    }

    public void reopen() {
        if (this.status == PeriodStatus.LOCKED) {
            throw new IllegalStateException("Cannot reopen locked period without audit unlock");
        }
        this.status = PeriodStatus.OPEN;
    }
}
