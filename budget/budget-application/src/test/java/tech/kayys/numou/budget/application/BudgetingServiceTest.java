package tech.kayys.numou.budget.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.budget.domain.*;
import tech.kayys.numou.budget.spi.port.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BudgetingServiceTest {
    @Test
    void lifecycle_versioning_and_commitment_control() {
        var service = newService();
        var id = new BudgetId("B-2026");
        var budget = service.create("tenant-a", id, "Operating plan", 2026);
        budget.setLine("6100", Map.of("costCenter", "IT"), new BudgetPeriod(2026, 1), new BigDecimal("1000"));
        budget.submitForReview();
        budget.approve();
        budget.activate();
        assertEquals(PlanningBudgetStatus.ACTIVE, budget.status());
        assertEquals(2, budget.createVersion());
        var commitment = service.commit("tenant-a", new CommitmentId("C-1"), id, "6100",
                Map.of("costCenter", "IT"), new BudgetPeriod(2026, 1),
                new BigDecimal("500"), "PO-1");
        assertEquals(CommitmentStatus.OPEN, commitment.status());
        assertEquals(BudgetControlOutcome.ALLOW,
                service.checkAvailability("tenant-a", id, "6100", Map.of("costCenter", "IT"), new BudgetPeriod(2026, 1),
                        new BigDecimal("100")).outcome());
    }

    @Test
    void forecast_and_allocation_are_deterministic() {
        var service = newService();
        var future = List.of(new BudgetPeriod(2026, 4), new BudgetPeriod(2026, 5));
        assertEquals(new BigDecimal("400"), service.forecast(
                List.of(new BudgetPeriod(2026, 1), new BudgetPeriod(2026, 2), new BudgetPeriod(2026, 3)),
                List.of(new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300")), future).get(future.get(0)));
        assertEquals(new BigDecimal("250"), service.allocate(new BigDecimal("1000"),
                Map.of("A", new BigDecimal("1"), "B", new BigDecimal("3"))).get("A"));
    }

    private BudgetingService newService() {
        return new BudgetingService(new TestBudgetRepository(),
                new TestCommitmentRepository(), new TestActualRepository());
    }

    private static final class TestBudgetRepository implements BudgetRepository {
        private final Map<String, PlanningBudget> budgets = new HashMap<>();

        @Override
        public PlanningBudget save(PlanningBudget budget) {
            budgets.put(key(budget.tenantId(), budget.id()), budget);
            return budget;
        }

        @Override
        public Optional<PlanningBudget> find(String tenantId, BudgetId id) {
            return Optional.ofNullable(budgets.get(key(tenantId, id)));
        }
        private String key(String tenantId, BudgetId id) { return tenantId + "|" + id.value(); }
    }

    private static final class TestCommitmentRepository implements CommitmentRepository {
        private final Map<String, BudgetCommitment> commitments = new HashMap<>();

        @Override
        public BudgetCommitment save(BudgetCommitment commitment) {
            commitments.put(key(commitment.tenantId(), commitment.id()), commitment);
            return commitment;
        }

        @Override
        public Optional<BudgetCommitment> find(String tenantId, CommitmentId id) {
            return Optional.ofNullable(commitments.get(key(tenantId, id)));
        }

        @Override
        public List<BudgetCommitment> openFor(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period) {
            return commitments.values().stream()
                    .filter(commitment -> commitment.tenantId().equals(tenantId) && commitment.budgetId().equals(budgetId)
                            && commitment.accountCode().equals(accountCode)
                            && commitment.period().equals(period)
                            && commitment.status() != CommitmentStatus.RELEASED
                            && commitment.status() != CommitmentStatus.CONSUMED)
                    .toList();
        }
        private String key(String tenantId, CommitmentId id) { return tenantId + "|" + id.value(); }
    }

    private static final class TestActualRepository implements ActualRepository {
        private final Map<String, BigDecimal> actuals = new HashMap<>();

        @Override
        public void add(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period, BigDecimal amount) {
            actuals.merge(key(tenantId, budgetId, accountCode, period), amount, BigDecimal::add);
        }

        @Override
        public BigDecimal totalFor(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period) {
            return actuals.getOrDefault(key(tenantId, budgetId, accountCode, period), BigDecimal.ZERO);
        }

        private String key(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period) {
            return tenantId + "|" + budgetId + "|" + accountCode + "|" + period;
        }
    }
}
