package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalYearId;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FiscalYear extends AbstractAggregateRoot<FiscalYearId> {
    private final FiscalYearId id;
    private final int year;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private boolean closed;
    private final List<FiscalPeriod> periods = new ArrayList<>();

    public FiscalYear(FiscalYearId id, int year, LocalDate startDate, LocalDate endDate) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.year = year;
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = Objects.requireNonNull(endDate);
        this.closed = false;
    }

    @Override
    public FiscalYearId id() { return id; }
    public FiscalYearId getId() { return id; }
    public int getYear() { return year; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isClosed() { return closed; }
    public List<FiscalPeriod> getPeriods() { return Collections.unmodifiableList(periods); }

    public void addPeriod(FiscalPeriod period) {
        periods.add(Objects.requireNonNull(period, "period cannot be null"));
    }

    public FiscalPeriod findPeriodFor(LocalDate date) {
        for (FiscalPeriod p : periods) {
            if (p.contains(date)) {
                return p;
            }
        }
        return null;
    }

    public void closeYear() {
        for (FiscalPeriod p : periods) {
            if (p.isOpen()) {
                p.close();
            }
        }
        this.closed = true;
    }
}
