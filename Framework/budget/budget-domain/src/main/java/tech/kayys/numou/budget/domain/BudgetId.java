package tech.kayys.numou.budget.domain;

import java.util.Objects;

public record BudgetId(String value) {
    public BudgetId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("budget id must not be blank");
    }
    @Override public String toString() { return value; }
}
