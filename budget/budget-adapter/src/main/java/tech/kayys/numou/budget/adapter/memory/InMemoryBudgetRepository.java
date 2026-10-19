package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.spi.port.BudgetRepository;
import tech.kayys.numou.budget.domain.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBudgetRepository implements BudgetRepository {
    private final Map<BudgetId, PlanningBudget> budgets = new ConcurrentHashMap<>();
    @Override public PlanningBudget save(PlanningBudget budget) { budgets.put(budget.id(), budget); return budget; }
    @Override public Optional<PlanningBudget> find(BudgetId id) { return Optional.ofNullable(budgets.get(id)); }
}
