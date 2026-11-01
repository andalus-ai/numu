package tech.kayys.numou.budget.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.budget.domain.*;
import tech.kayys.numou.budget.spi.port.BudgetStorePort;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTest {

    @Test
    void budget_control_availability_checks() {
        var service = new BudgetService(new TestBudgetStore());
        var budget = new Budget(
                "B-2026-IT",
                "t1",
                "CORP",
                "2026",
                "CC-IT",
                "account-it",
                Money.of(new BigDecimal("10000"), "USD")
        );
        service.register(budget);

        // Check 5000 -> ALLOW
        var r1 = service.checkAvailability("t1", "B-2026-IT", Money.of(new BigDecimal("5000"), "USD"));
        assertEquals(BudgetControlOutcome.ALLOW, r1.outcome());

        // Check 9000 (>80% of 10000) -> WARN
        var r2 = service.checkAvailability("B-2026-IT", Money.of(new BigDecimal("9000"), "USD"));
        assertEquals(BudgetControlOutcome.WARN, r2.outcome());

        // Check 12000 -> BLOCK
        var r3 = service.checkAvailability("B-2026-IT", Money.of(new BigDecimal("12000"), "USD"));
        assertEquals(BudgetControlOutcome.BLOCK, r3.outcome());
    }

    @Test
    void variance_percentage_calculation() {
        var service = new BudgetService(new TestBudgetStore());
        var budget = new Budget(
                "B-2026-OPS",
                "t1",
                "CORP",
                "2026",
                "CC-OPS",
                "account-ops",
                Money.of(new BigDecimal("10000"), "USD")
        );
        budget.relieveAndSpend(Money.zero("USD"), Money.of(new BigDecimal("8000"), "USD"));
        service.register(budget);

        // 8000 - 10000 = -2000 / 10000 = -20.00%
        var variance = service.calculateVariancePercentage("t1", "B-2026-OPS");
        assertEquals(new BigDecimal("-20.0000"), variance);
    }

    private static final class TestBudgetStore implements BudgetStorePort {
        private final Map<String, Budget> budgets = new HashMap<>();

        @Override
        public Optional<Budget> find(String tenantId, String budgetId) {
            return Optional.ofNullable(budgets.get(tenantId + "|" + budgetId));
        }

        @Override
        public void save(Budget budget) {
            budgets.put(budget.tenantId() + "|" + budget.budgetId(), budget);
        }
    }
}
