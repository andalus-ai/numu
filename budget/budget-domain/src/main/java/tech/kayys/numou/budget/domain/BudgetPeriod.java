package tech.kayys.numou.budget.domain;

import java.time.YearMonth;

/** A fiscal month used by budget lines, actuals, and forecasts. */
public record BudgetPeriod(int year, int month) implements Comparable<BudgetPeriod> {
    public BudgetPeriod {
        YearMonth.of(year, month);
    }
    public static BudgetPeriod of(YearMonth value) {
        return new BudgetPeriod(value.getYear(), value.getMonthValue());
    }
    public YearMonth yearMonth() { return YearMonth.of(year, month); }
    @Override public int compareTo(BudgetPeriod other) {
        return yearMonth().compareTo(other.yearMonth());
    }
    @Override public String toString() { return year + "-" + "%02d".formatted(month); }
}
