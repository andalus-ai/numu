package tech.kayys.numou.accounting.integrations.local;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.budget.adapter.memory.InMemoryActualRepository;
import tech.kayys.numou.budget.adapter.memory.InMemoryBudgetRepository;
import tech.kayys.numou.budget.adapter.memory.InMemoryBudgetStore;
import tech.kayys.numou.budget.adapter.memory.InMemoryCommitmentRepository;
import tech.kayys.numou.budget.application.BudgetService;
import tech.kayys.numou.budget.application.BudgetingService;
import tech.kayys.numou.inventory.adapter.memory.InMemoryInventoryRepository;
import tech.kayys.numou.inventory.application.InventoryService;
import tech.kayys.numou.project.adapter.memory.InMemoryProjectFinanceStore;
import tech.kayys.numou.project.adapter.memory.InMemoryProjectRepository;
import tech.kayys.numou.project.application.project.ProjectFinanceService;

/**
 * Opt-in local composition of Accounting with Project, Budget, and Inventory.
 *
 * <p>Every bean produced here is backed by an in-memory adapter. This module is
 * intended for local development, demonstrations, and isolated tests only.
 * It is deliberately not a production persistence configuration. A production
 * host must select durable adapters and include this module only if it
 * explicitly wants these in-process integrations.</p>
 */
@ApplicationScoped
public class AccountingLocalIntegrationsProducer {

    @Produces
    @ApplicationScoped
    public InventoryService inventoryService() {
        return new InventoryService(new InMemoryInventoryRepository());
    }

    @Produces
    @ApplicationScoped
    public BudgetService budgetService() {
        return new BudgetService(new InMemoryBudgetStore());
    }

    @Produces
    @ApplicationScoped
    public BudgetingService budgetingService() {
        return new BudgetingService(
                new InMemoryBudgetRepository(),
                new InMemoryCommitmentRepository(),
                new InMemoryActualRepository());
    }

    @Produces
    @ApplicationScoped
    public ProjectFinanceService projectFinanceService() {
        return new ProjectFinanceService(
                new InMemoryProjectRepository(),
                new InMemoryProjectFinanceStore());
    }
}
