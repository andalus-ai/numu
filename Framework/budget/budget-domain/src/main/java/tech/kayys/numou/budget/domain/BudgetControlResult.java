package tech.kayys.numou.budget.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/** Result emitted by budget control evaluation. */
public record BudgetControlResult(
        BudgetControlOutcome outcome,
        String message,
        Money requestedAmount,
        Money availableAmount
) {
    public BudgetControlResult {
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(message);
        Objects.requireNonNull(requestedAmount);
        Objects.requireNonNull(availableAmount);
    }

    public static BudgetControlResult allow(Money requested, Money available) {
        return new BudgetControlResult(BudgetControlOutcome.ALLOW, "Budget available", requested, available);
    }

    public static BudgetControlResult block(Money requested, Money available) {
        return new BudgetControlResult(BudgetControlOutcome.BLOCK, "Insufficient budget available", requested, available);
    }

    public static BudgetControlResult warn(String msg, Money requested, Money available) {
        return new BudgetControlResult(BudgetControlOutcome.WARN, msg, requested, available);
    }
}
