package tech.kayys.numou.budget.application;

import tech.kayys.numou.budget.domain.*;
import tech.kayys.numou.budget.spi.port.BudgetStorePort;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service orchestrating budget registration, control checks, and variance reporting.
 */
public final class BudgetService {

    private final BudgetStorePort budgets;

    public BudgetService(BudgetStorePort budgets) {
        this.budgets = Objects.requireNonNull(budgets, "budgets cannot be null");
    }

    public void register(Budget budget) {
        budgets.save(budget);
    }

    public BudgetControlResult checkAvailability(String tenantId, String budgetId, Money requested) {
        requireTenant(tenantId);
        Budget budget = budgets.find(tenantId, budgetId).orElse(null);
        if (budget == null) {
            return BudgetControlResult.block(requested, Money.zero(requested.currency().code()));
        }

        Money available = budget.availableBalance();
        if (available.amount().compareTo(requested.amount()) < 0) {
            return BudgetControlResult.block(requested, available);
        }

        // Warn if requested is > 80% of remaining available
        if (requested.amount().compareTo(available.amount().multiply(new BigDecimal("0.80"))) > 0) {
            return BudgetControlResult.warn("Budget nearing exhaustion (>80% requested)", requested, available);
        }

        return BudgetControlResult.allow(requested, available);
    }

    /**
     * Computes variance = actualSpent - allocated.
     * Negative variance means under budget (favorable); positive means over budget.
     */
    public BigDecimal calculateVariancePercentage(String tenantId, String budgetId) {
        requireTenant(tenantId);
        Budget budget = budgets.find(tenantId, budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));

        BigDecimal allocated = budget.allocatedAmount().amount();
        if (allocated.signum() == 0) return BigDecimal.ZERO;

        BigDecimal spent = budget.actualSpentAmount().amount();
        return spent.subtract(allocated)
                .divide(allocated, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    public Optional<Budget> find(String tenantId, String budgetId) {
        requireTenant(tenantId);
        return budgets.find(tenantId, budgetId);
    }

    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    }
}
