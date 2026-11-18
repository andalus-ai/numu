package tech.kayys.numou.budget.spi.port;

import tech.kayys.numou.budget.domain.Budget;

import java.util.Optional;

/** Persistence port for the operational budget aggregate. */
public interface BudgetStorePort {
    Optional<Budget> find(String tenantId, String budgetId);

    void save(Budget budget);
}
