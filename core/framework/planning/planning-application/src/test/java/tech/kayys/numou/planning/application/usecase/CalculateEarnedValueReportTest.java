package tech.kayys.numou.planning.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tech.kayys.numou.planning.domain.BudgetedCostEvidence;
import tech.kayys.numou.planning.domain.CostEvidenceStatus;
import tech.kayys.numou.planning.domain.CumulativeActualCostEvidence;

class CalculateEarnedValueReportTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STRUCTURE = UUID.randomUUID();
    private static final UUID BASELINE = UUID.randomUUID();
    private static final UUID NODE = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 24);

    @Test
    void calculatesNodeAndPortfolioMetricsFromApprovedScopedEvidence() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        var result = useCase.execute(command());

        assertEquals(1, result.nodes().size());
        assertEquals(new BigDecimal("600.00"), result.nodes().get(0).earnedValue());
        assertEquals(new BigDecimal("700.00"), result.nodes().get(0).plannedValue());
        assertTrue(result.portfolio().isPresent());
        assertEquals(new BigDecimal("1.2000"), result.portfolio().orElseThrow().costPerformanceIndex());
    }

    @Test
    void rejectsMissingActualCostEvidenceInsteadOfReturningPartialReport() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of());

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsEvidenceFromAnotherTenant() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(new BudgetedCostEvidence(
                        UUID.randomUUID(), STRUCTURE, NODE, BASELINE, "USD", new BigDecimal("1000"),
                        "budget:1", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-01T00:00:00Z"))),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsDuplicateProgressNodesAtCommandBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new CalculateEarnedValueReport.Command(
                TENANT, STRUCTURE, BASELINE, DATE, "USD",
                List.of(progress(NODE), progress(NODE))));
    }

    @Test
    void returnsNodeMetricsWithoutUndefinedPortfolioRatios() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "0")));
        var zeroProgress = new CalculateEarnedValueReport.Command(
                TENANT, STRUCTURE, BASELINE, DATE, "USD",
                List.of(new CalculateEarnedValueReport.NodeProgress(
                        NODE, BigDecimal.ZERO, BigDecimal.ZERO)));

        var result = useCase.execute(zeroProgress);
        assertEquals(1, result.nodes().size());
        assertTrue(result.portfolio().isEmpty());
    }


    @Test
    void rejectsBudgetEvidenceForAnotherBaseline() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, UUID.randomUUID(), "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsActualCostForAnotherStructureOrReportingDate() {
        var wrongStructure = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(new CumulativeActualCostEvidence(
                        TENANT, UUID.randomUUID(), NODE, date, "USD", new BigDecimal("500"),
                        "actual:wrong-structure", CostEvidenceStatus.APPROVED,
                        Instant.parse("2026-10-24T00:00:00Z"))));
        assertThrows(IllegalArgumentException.class, () -> wrongStructure.execute(command()));

        var wrongDate = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date.minusDays(1), "500")));
        assertThrows(IllegalArgumentException.class, () -> wrongDate.execute(command()));
    }

    @Test
    void rejectsMixedCurrenciesRatherThanSilentlyConverting() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(new BudgetedCostEvidence(
                        TENANT, STRUCTURE, NODE, BASELINE, "EUR", new BigDecimal("1000"),
                        "budget:eur", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-01T00:00:00Z"))),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsDuplicateBudgetEvidenceForSameNode() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(
                        budget(NODE, BASELINE, "1000"), budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsUnrequestedCostEvidenceNode() {
        var useCase = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(UUID.randomUUID(), BASELINE, "1000")),
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(command()));
    }

    @Test
    void rejectsNullProviderResults() {
        var nullBudgetSource = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> null,
                (tenant, structure, date, nodes) -> List.of(actual(NODE, date, "500")));
        assertThrows(NullPointerException.class, () -> nullBudgetSource.execute(command()));

        var nullActualSource = new CalculateEarnedValueReport(
                (tenant, structure, baseline, nodes) -> List.of(budget(NODE, BASELINE, "1000")),
                (tenant, structure, date, nodes) -> null);
        assertThrows(NullPointerException.class, () -> nullActualSource.execute(command()));
    }

    private static CalculateEarnedValueReport.Command command() {
        return new CalculateEarnedValueReport.Command(
                TENANT, STRUCTURE, BASELINE, DATE, "USD", List.of(progress(NODE)));
    }

    private static CalculateEarnedValueReport.NodeProgress progress(UUID node) {
        return new CalculateEarnedValueReport.NodeProgress(
                node, new BigDecimal("70"), new BigDecimal("60"));
    }

    private static BudgetedCostEvidence budget(UUID node, UUID baseline, String amount) {
        return new BudgetedCostEvidence(TENANT, STRUCTURE, node, baseline, "USD",
                new BigDecimal(amount), "budget:1", CostEvidenceStatus.APPROVED,
                Instant.parse("2026-10-01T00:00:00Z"));
    }

    private static CumulativeActualCostEvidence actual(UUID node, LocalDate date, String amount) {
        return new CumulativeActualCostEvidence(TENANT, STRUCTURE, node, date, "USD",
                new BigDecimal(amount), "actual:1", CostEvidenceStatus.APPROVED,
                Instant.parse("2026-10-24T00:00:00Z"));
    }
}
