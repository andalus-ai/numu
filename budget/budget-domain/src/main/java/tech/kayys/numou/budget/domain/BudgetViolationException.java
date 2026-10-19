package tech.kayys.numou.budget.domain;

/** Raised when a budget command violates its lifecycle or accounting rules. */
public class BudgetViolationException extends IllegalStateException {
    public BudgetViolationException(String message) {
        super(message);
    }
}
