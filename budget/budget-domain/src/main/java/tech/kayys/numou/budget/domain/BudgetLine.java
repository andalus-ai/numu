package tech.kayys.numou.budget.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Immutable planning amount keyed by account, dimensions, and fiscal period. */
public record BudgetLine(
        String id,
        String accountCode,
        Map<String, String> dimensions,
        BudgetPeriod period,
        BigDecimal amount,
        BudgetLineStatus status
) {
    public BudgetLine {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("line id must not be blank");
        if (accountCode == null || accountCode.isBlank()) throw new IllegalArgumentException("account code must not be blank");
        dimensions = Map.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        Objects.requireNonNull(period, "period");
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("line amount must not be negative");
        Objects.requireNonNull(status, "status");
    }
    public boolean matches(String account, Map<String, String> dimensions, BudgetPeriod period) {
        return accountCode.equals(account) && this.dimensions.equals(dimensions) && this.period.equals(period);
    }
    public BudgetLine withAmount(BigDecimal value) {
        return new BudgetLine(id, accountCode, dimensions, period, value, status);
    }
}
