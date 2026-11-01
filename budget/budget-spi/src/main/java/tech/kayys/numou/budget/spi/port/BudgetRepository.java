package tech.kayys.numou.budget.spi.port;

import tech.kayys.numou.budget.domain.BudgetId;
import tech.kayys.numou.budget.domain.PlanningBudget;

import java.util.Optional;

public interface BudgetRepository {
    PlanningBudget save(PlanningBudget budget);
    Optional<PlanningBudget> find(String tenantId, BudgetId id);
}
