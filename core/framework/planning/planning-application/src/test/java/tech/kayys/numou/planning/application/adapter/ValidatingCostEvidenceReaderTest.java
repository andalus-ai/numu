package tech.kayys.numou.planning.application.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.kayys.numou.planning.application.port.out.CostEvidenceReader;
import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CostEvidenceStatus;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

class ValidatingCostEvidenceReaderTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STRUCTURE = UUID.randomUUID();
    private static final UUID BASELINE = UUID.randomUUID();
    private static final UUID NODE = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 24);

    @Test
    void acceptsApprovedEvidenceWithinExactRequestScope() {
        var reader = new ValidatingCostEvidenceReader(new StubReader(
                List.of(budget(TENANT, STRUCTURE, NODE, BASELINE, CostEvidenceStatus.APPROVED)),
                List.of(actual(TENANT, STRUCTURE, NODE, DATE, CostEvidenceStatus.APPROVED))));

        assertEquals(1, reader.findApprovedBudgets(TENANT, STRUCTURE, BASELINE, List.of(NODE)).size());
        assertEquals(1, reader.findApprovedActualCosts(TENANT, STRUCTURE, DATE, List.of(NODE)).size());
    }

    @Test
    void rejectsCrossTenantBudgetEvidence() {
        var reader = new ValidatingCostEvidenceReader(new StubReader(
                List.of(budget(UUID.randomUUID(), STRUCTURE, NODE, BASELINE, CostEvidenceStatus.APPROVED)),
                List.of()));

        assertThrows(IllegalStateException.class,
                () -> reader.findApprovedBudgets(TENANT, STRUCTURE, BASELINE, List.of(NODE)));
    }

    @Test
    void rejectsUnapprovedEvidenceAndDuplicateRows() {
        var unapproved = new ValidatingCostEvidenceReader(new StubReader(
                List.of(budget(TENANT, STRUCTURE, NODE, BASELINE, CostEvidenceStatus.PENDING)),
                List.of()));
        assertThrows(IllegalStateException.class,
                () -> unapproved.findApprovedBudgets(TENANT, STRUCTURE, BASELINE, List.of(NODE)));

        var duplicate = new ValidatingCostEvidenceReader(new StubReader(
                List.of(
                        budget(TENANT, STRUCTURE, NODE, BASELINE, CostEvidenceStatus.APPROVED),
                        budget(TENANT, STRUCTURE, NODE, BASELINE, CostEvidenceStatus.APPROVED)),
                List.of()));
        assertThrows(IllegalStateException.class,
                () -> duplicate.findApprovedBudgets(TENANT, STRUCTURE, BASELINE, List.of(NODE)));
    }

    @Test
    void rejectsActualCostFromAnotherReportingDate() {
        var reader = new ValidatingCostEvidenceReader(new StubReader(
                List.of(),
                List.of(actual(TENANT, STRUCTURE, NODE, DATE.minusDays(1), CostEvidenceStatus.APPROVED))));

        assertThrows(IllegalStateException.class,
                () -> reader.findApprovedActualCosts(TENANT, STRUCTURE, DATE, List.of(NODE)));
    }

    @Test
    void rejectsUnrequestedNode() {
        var reader = new ValidatingCostEvidenceReader(new StubReader(
                List.of(budget(TENANT, STRUCTURE, UUID.randomUUID(), BASELINE, CostEvidenceStatus.APPROVED)),
                List.of()));

        assertThrows(IllegalStateException.class,
                () -> reader.findApprovedBudgets(TENANT, STRUCTURE, BASELINE, List.of(NODE)));
    }

    private static BudgetedCostEvidence budget(
            UUID tenant, UUID structure, UUID node, UUID baseline, CostEvidenceStatus status) {
        Instant approvedAt = status == CostEvidenceStatus.APPROVED
                ? Instant.parse("2026-10-24T00:00:00Z") : null;
        return new BudgetedCostEvidence(tenant, structure, node, baseline, "USD",
                new BigDecimal("100.00"), "budget:" + node, status, approvedAt);
    }

    private static CumulativeActualCostEvidence actual(
            UUID tenant, UUID structure, UUID node, LocalDate date, CostEvidenceStatus status) {
        Instant approvedAt = status == CostEvidenceStatus.APPROVED
                ? Instant.parse("2026-10-24T00:00:00Z") : null;
        return new CumulativeActualCostEvidence(tenant, structure, node, date, "USD",
                new BigDecimal("25.00"), "actual:" + node, status, approvedAt);
    }

    private record StubReader(
            List<BudgetedCostEvidence> budgets,
            List<CumulativeActualCostEvidence> actuals) implements CostEvidenceReader {
        @Override
        public List<BudgetedCostEvidence> findApprovedBudgets(
                UUID tenantId, UUID structureId, UUID baselineId, List<UUID> nodeIds) {
            return budgets;
        }

        @Override
        public List<CumulativeActualCostEvidence> findApprovedActualCosts(
                UUID tenantId, UUID structureId, LocalDate asOfDate, List<UUID> nodeIds) {
            return actuals;
        }
    }
}
