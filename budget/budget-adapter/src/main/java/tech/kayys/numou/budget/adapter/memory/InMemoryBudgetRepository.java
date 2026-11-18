package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.spi.port.BudgetRepository;
import tech.kayys.numou.budget.domain.*;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBudgetRepository implements BudgetRepository {
    private final Map<Key, PlanningBudget> budgets = new ConcurrentHashMap<>();
    @Override public PlanningBudget save(PlanningBudget budget) { budgets.put(new Key(budget.tenantId(), budget.id()), budget); return budget; }
    @Override public Optional<PlanningBudget> find(String tenantId, BudgetId id) { return Optional.ofNullable(budgets.get(new Key(tenantId, id))); }
    private record Key(String tenantId, BudgetId budgetId) {}
}
