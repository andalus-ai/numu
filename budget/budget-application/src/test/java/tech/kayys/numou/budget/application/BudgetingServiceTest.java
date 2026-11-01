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
    void tenant_scope_is_enforced_for_budgets_commitments_and_actuals() {
        var service = newService();
        var sharedBudgetId = new BudgetId("SHARED-BUDGET");
        var period = new BudgetPeriod(2026, 1);

        var tenantA = service.create("tenant-a", sharedBudgetId, "Tenant A plan", 2026);
        tenantA.setLine("6100", Map.of(), period, new BigDecimal("1000"));
        tenantA.submitForReview();
        tenantA.approve();
        tenantA.activate();

        var tenantB = service.create("tenant-b", sharedBudgetId, "Tenant B plan", 2026);
        tenantB.setLine("6100", Map.of(), period, new BigDecimal("50"));
        tenantB.submitForReview();
        tenantB.approve();
        tenantB.activate();

        var sharedCommitmentId = new CommitmentId("SHARED-COMMITMENT");
        service.commit("tenant-a", sharedCommitmentId, sharedBudgetId, "6100",
                period, new BigDecimal("300"), "REQ-A");
        service.commit("tenant-b", sharedCommitmentId, sharedBudgetId, "6100",
                period, new BigDecimal("20"), "REQ-B");

        assertEquals("Tenant A plan", service.require("tenant-a", sharedBudgetId).code());
        assertEquals("Tenant B plan", service.require("tenant-b", sharedBudgetId).code());
        assertEquals(new BigDecimal("700"), service.checkAvailability("tenant-a", sharedBudgetId,
                "6100", Map.of(), period, new BigDecimal("1")).available());
        assertEquals(new BigDecimal("30"), service.checkAvailability("tenant-b", sharedBudgetId,
                "6100", Map.of(), period, new BigDecimal("1")).available());

        service.recordActual("tenant-a", sharedBudgetId, "6100", period, new BigDecimal("100"));
        assertEquals(new BigDecimal("30"), service.checkAvailability("tenant-b", sharedBudgetId,
                "6100", Map.of(), period, new BigDecimal("1")).available());

        assertThrows(java.util.NoSuchElementException.class,
                () -> service.consume("tenant-c", sharedCommitmentId, BigDecimal.ONE));
        service.consume("tenant-b", sharedCommitmentId, new BigDecimal("5"));
        assertEquals(new BigDecimal("600"), service.checkAvailability("tenant-a", sharedBudgetId,
                "6100", Map.of(), period, BigDecimal.ONE).available(),
                "consuming tenant B's commitment must not change tenant A's availability");
        assertEquals(new BigDecimal("35"), service.checkAvailability("tenant-b", sharedBudgetId,
                "6100", Map.of(), period, BigDecimal.ONE).available());
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
