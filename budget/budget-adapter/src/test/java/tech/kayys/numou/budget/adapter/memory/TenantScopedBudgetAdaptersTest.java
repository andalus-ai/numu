package tech.kayys.numou.budget.adapter.memory;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.budget.domain.*;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TenantScopedBudgetAdaptersTest {
    @Test
    void planningBudgetCommitmentAndActualStoresSeparateTenantsWithIdenticalIds() {
        var budgets = new InMemoryBudgetRepository();
        var commitments = new InMemoryCommitmentRepository();
        var actuals = new InMemoryActualRepository();
        var budgetId = new BudgetId("shared-budget");
        var commitmentId = new CommitmentId("shared-commitment");
        var period = new BudgetPeriod(2026, 1);

        budgets.save(new PlanningBudget("tenant-a", budgetId, "A", 2026));
        budgets.save(new PlanningBudget("tenant-b", budgetId, "B", 2026));
        assertEquals("A", budgets.find("tenant-a", budgetId).orElseThrow().code());
        assertEquals("B", budgets.find("tenant-b", budgetId).orElseThrow().code());
        assertTrue(budgets.find("tenant-c", budgetId).isEmpty());

        commitments.save(new BudgetCommitment("tenant-a", commitmentId, budgetId, "6100",
                period, new BigDecimal("100"), "REQ-A"));
        commitments.save(new BudgetCommitment("tenant-b", commitmentId, budgetId, "6100",
                period, new BigDecimal("25"), "REQ-B"));
        assertEquals(1, commitments.openFor("tenant-a", budgetId, "6100", period).size());
        assertEquals(new BigDecimal("100"), commitments.find("tenant-a", commitmentId).orElseThrow().remaining());
        assertEquals(new BigDecimal("25"), commitments.find("tenant-b", commitmentId).orElseThrow().remaining());
        assertTrue(commitments.find("tenant-c", commitmentId).isEmpty());

        actuals.add("tenant-a", budgetId, "6100", period, new BigDecimal("12"));
        assertEquals(new BigDecimal("12"), actuals.totalFor("tenant-a", budgetId, "6100", period));
        assertEquals(BigDecimal.ZERO, actuals.totalFor("tenant-b", budgetId, "6100", period));
    }

    @Test
    void operationalBudgetStoreRequiresTenantForLookup() {
        var store = new InMemoryBudgetStore();
        store.save(new Budget("shared", "tenant-a", "LEDGER", "2026", "CC-A", "6100",
                Money.of(new BigDecimal("10"), "IDR")));
        store.save(new Budget("shared", "tenant-b", "LEDGER", "2026", "CC-B", "6100",
                Money.of(new BigDecimal("20"), "IDR")));

        assertEquals(new BigDecimal("10"), store.find("tenant-a", "shared").orElseThrow()
                .allocatedAmount().amount());
        assertEquals(new BigDecimal("20"), store.find("tenant-b", "shared").orElseThrow()
                .allocatedAmount().amount());
        assertTrue(store.find("tenant-c", "shared").isEmpty());
    }
}
