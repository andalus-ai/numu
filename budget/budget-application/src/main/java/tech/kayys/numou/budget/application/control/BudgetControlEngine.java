package tech.kayys.numou.budget.application.control;

import tech.kayys.numou.budget.spi.port.*;
import tech.kayys.numou.budget.domain.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class BudgetControlEngine {
    private final BudgetRepository budgets;
    private final CommitmentRepository commitments;
    private final ActualRepository actuals;
    public BudgetControlEngine(BudgetRepository budgets, CommitmentRepository commitments, ActualRepository actuals) {
        this.budgets = Objects.requireNonNull(budgets);
        this.commitments = Objects.requireNonNull(commitments);
        this.actuals = Objects.requireNonNull(actuals);
    }
    public Availability check(String tenantId, BudgetId id, String account, Map<String, String> dimensions,
                              BudgetPeriod period, BigDecimal requested) {
        requireTenant(tenantId);
        if (requested == null || requested.signum() <= 0) throw new IllegalArgumentException("requested amount must be positive");
        var budget = budgets.find(tenantId, id).orElseThrow(() -> new java.util.NoSuchElementException("unknown budget: " + id));
        BigDecimal allocated = budget.activeVersion().totalFor(account, dimensions, period);
        BigDecimal reserved = commitments.openFor(tenantId, id, account, period).stream()
                .map(BudgetCommitment::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = actuals.totalFor(tenantId, id, account, period);
        BigDecimal available = allocated.subtract(reserved).subtract(spent);
        if (available.compareTo(requested) < 0)
            return new Availability(BudgetControlOutcome.BLOCK, allocated, reserved, spent, available,
                    "requested amount exceeds available budget");
        BigDecimal utilization = allocated.signum() == 0 ? BigDecimal.ONE
                : reserved.add(spent).add(requested).divide(allocated, java.math.MathContext.DECIMAL64);
        var outcome = utilization.compareTo(new BigDecimal("0.80")) > 0
                ? BudgetControlOutcome.WARN : BudgetControlOutcome.ALLOW;
        return new Availability(outcome, allocated, reserved, spent, available,
                outcome == BudgetControlOutcome.WARN ? "budget utilization exceeds 80%" : "budget available");
    }
    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    }
}
