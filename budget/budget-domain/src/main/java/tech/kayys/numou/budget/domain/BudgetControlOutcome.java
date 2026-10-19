package tech.kayys.numou.budget.domain;

/** Outcome of budget availability control checks. */
public enum BudgetControlOutcome {
    ALLOW, WARN, BLOCK, APPROVAL_REQUIRED
}
