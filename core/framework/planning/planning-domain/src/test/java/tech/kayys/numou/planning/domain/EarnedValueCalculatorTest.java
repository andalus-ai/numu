package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EarnedValueCalculatorTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID STRUCTURE = UUID.randomUUID();
    private static final UUID NODE = UUID.randomUUID();
    private static final UUID BASELINE = UUID.randomUUID();
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 10, 24);

    @Test
    void calculatesCostAndScheduleMetricsFromApprovedEvidence() {
        var budget = budget("1000.00", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-20T10:00:00Z"));
        var actual = actual("600.00", CostEvidenceStatus.APPROVED, REPORT_DATE, Instant.parse("2026-10-24T10:00:00Z"));

        var metrics = EarnedValueCalculator.calculate(
                budget, actual, REPORT_DATE, new BigDecimal("70"), new BigDecimal("60"));

        assertEquals(new BigDecimal("700.00"), metrics.plannedValue());
        assertEquals(new BigDecimal("600.00"), metrics.earnedValue());
        assertEquals(new BigDecimal("600.00"), metrics.actualCost());
        assertEquals(new BigDecimal("0.00"), metrics.costVariance());
        assertEquals(new BigDecimal("-100.00"), metrics.scheduleVariance());
        assertEquals(new BigDecimal("1.0000"), metrics.costPerformanceIndex().orElseThrow());
        assertEquals(new BigDecimal("0.8571"), metrics.schedulePerformanceIndex().orElseThrow());
        assertTrue(metrics.isBehindPlan());
        assertFalse(metrics.isOverBudget());
    }

    @Test
    void leavesPerformanceIndicesUndefinedWhenDenominatorsAreZero() {
        var budget = budget("1000", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-20T10:00:00Z"));
        var actual = actual("0", CostEvidenceStatus.APPROVED, REPORT_DATE, Instant.parse("2026-10-24T10:00:00Z"));

        var metrics = EarnedValueCalculator.calculate(
                budget, actual, REPORT_DATE, BigDecimal.ZERO, BigDecimal.ZERO);

        assertTrue(metrics.costPerformanceIndex().isEmpty());
        assertTrue(metrics.schedulePerformanceIndex().isEmpty());
    }

    @Test
    void rejectsUnapprovedEvidence() {
        var budget = budget("1000", CostEvidenceStatus.DRAFT, null);
        var actual = actual("100", CostEvidenceStatus.APPROVED, REPORT_DATE, Instant.parse("2026-10-24T10:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> EarnedValueCalculator.calculate(
                budget, actual, REPORT_DATE, new BigDecimal("10"), new BigDecimal("10")));
    }

    @Test
    void rejectsMismatchedTenantAndCurrency() {
        var budget = budget("1000", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-20T10:00:00Z"));
        var wrongTenant = new CumulativeActualCostEvidence(
                UUID.randomUUID(), STRUCTURE, NODE, REPORT_DATE, "IDR", new BigDecimal("100"),
                "finance:batch:2", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-24T10:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> EarnedValueCalculator.calculate(
                budget, wrongTenant, REPORT_DATE, new BigDecimal("10"), new BigDecimal("10")));

        var wrongCurrency = actual("100", CostEvidenceStatus.APPROVED, REPORT_DATE, Instant.parse("2026-10-24T10:00:00Z"), "USD");
        assertThrows(IllegalArgumentException.class, () -> EarnedValueCalculator.calculate(
                budget, wrongCurrency, REPORT_DATE, new BigDecimal("10"), new BigDecimal("10")));
    }

    @Test
    void rejectsWrongReportDateAndPercentagesOutsideRange() {
        var budget = budget("1000", CostEvidenceStatus.APPROVED, Instant.parse("2026-10-20T10:00:00Z"));
        var actual = actual("100", CostEvidenceStatus.APPROVED, REPORT_DATE.minusDays(1), Instant.parse("2026-10-23T10:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> EarnedValueCalculator.calculate(
                budget, actual, REPORT_DATE, new BigDecimal("10"), new BigDecimal("10")));
        var matchingActual = actual("100", CostEvidenceStatus.APPROVED, REPORT_DATE, Instant.parse("2026-10-24T10:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> EarnedValueCalculator.calculate(
                budget, matchingActual, REPORT_DATE, new BigDecimal("101"), new BigDecimal("10")));
    }

    private static BudgetedCostEvidence budget(String amount, CostEvidenceStatus status, Instant approvedAt) {
        return new BudgetedCostEvidence(TENANT, STRUCTURE, NODE, BASELINE, "IDR",
                new BigDecimal(amount), "budget:baseline:approved", status, approvedAt);
    }

    private static CumulativeActualCostEvidence actual(
            String amount, CostEvidenceStatus status, LocalDate date, Instant approvedAt) {
        return actual(amount, status, date, approvedAt, "IDR");
    }

    private static CumulativeActualCostEvidence actual(
            String amount, CostEvidenceStatus status, LocalDate date, Instant approvedAt, String currency) {
        return new CumulativeActualCostEvidence(TENANT, STRUCTURE, NODE, date, currency,
                new BigDecimal(amount), "finance:cost-ledger:batch-42", status, approvedAt);
    }
}
