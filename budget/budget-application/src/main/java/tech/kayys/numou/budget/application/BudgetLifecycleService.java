package tech.kayys.numou.budget.application;

import tech.kayys.numou.budget.spi.port.BudgetRepository;
import tech.kayys.numou.budget.domain.*;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class BudgetLifecycleService {
    private final BudgetRepository budgets;
    public BudgetLifecycleService(BudgetRepository budgets) { this.budgets = Objects.requireNonNull(budgets); }
    public PlanningBudget create(String tenantId, BudgetId id, String code, int fiscalYear) {
        requireTenant(tenantId);
        if (budgets.find(tenantId, id).isPresent()) throw new BudgetViolationException("budget already exists: " + id);
        return budgets.save(new PlanningBudget(tenantId, id, code, fiscalYear));
    }
    public PlanningBudget require(String tenantId, BudgetId id) {
        requireTenant(tenantId);
        return budgets.find(tenantId, id).orElseThrow(() -> new NoSuchElementException("unknown budget: " + id));
    }
    private void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    }
}
