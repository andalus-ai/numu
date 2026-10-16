package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.LocalDate;
import java.util.Objects;

public final class FiscalPeriod extends AbstractAggregateRoot<FiscalPeriodId> {
    public enum PeriodStatus implements ValueObject { OPEN, CLOSED, LOCKED }

    private final FiscalPeriodId id;
    private final int periodNumber;
    private final String periodName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private PeriodStatus status;

    public FiscalPeriod(FiscalPeriodId id, int periodNumber, String periodName, LocalDate startDate, LocalDate endDate) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.periodNumber = periodNumber;
        this.periodName = Objects.requireNonNull(periodName);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.status = PeriodStatus.OPEN;
    }

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
