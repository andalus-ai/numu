package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.domain.Budget;
import tech.kayys.numou.budget.spi.port.BudgetStorePort;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory adapter for operational budgets. */
public final class InMemoryBudgetStore implements BudgetStorePort {
    private final ConcurrentHashMap<Key, Budget> budgets = new ConcurrentHashMap<>();

    @Override
    public Optional<Budget> find(String tenantId, String budgetId) {
        return Optional.ofNullable(budgets.get(new Key(tenantId, budgetId)));
    }

    @Override
    public void save(Budget budget) {
        budgets.put(new Key(budget.tenantId(), budget.budgetId()), budget);
    }
    private record Key(String tenantId, String budgetId) {}
}
