package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EarnedValuePortfolioReportTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID structure = UUID.randomUUID();
    private final UUID baseline = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 10, 24);

    @Test
    void rollsUpDistinctNodesAndRecomputesRatiosFromTotals() {
        var first = metrics(UUID.randomUUID(), "1000", "700", "600", "500");
        var second = metrics(UUID.randomUUID(), "500", "250", "250", "300");
        var report = EarnedValuePortfolioReport.fromNodes(List.of(first, second));

        assertEquals(new BigDecimal("1500.00"), report.budgetAtCompletion());
        assertEquals(new BigDecimal("950.00"), report.plannedValue());
        assertEquals(new BigDecimal("800.00"), report.earnedValue());
        assertEquals(new BigDecimal("800.00"), report.actualCost());
        assertEquals(new BigDecimal("0.00"), report.costVariance());
        assertEquals(new BigDecimal("-150.00"), report.scheduleVariance());
        assertEquals(new BigDecimal("1.0000"), report.costPerformanceIndex());
        assertEquals(new BigDecimal("0.8421"), report.schedulePerformanceIndex());
    }

    @Test
    void rejectsDuplicateNodesAndMixedCurrency() {
        var node = metrics(UUID.randomUUID(), "1000", "700", "600", "500");
        assertThrows(IllegalArgumentException.class, () -> EarnedValuePortfolioReport.fromNodes(List.of(node, node)));
        var usd = new EarnedValueMetrics(tenant, structure, UUID.randomUUID(), baseline, date, "USD",
                new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("50.00"),
                new BigDecimal("40.00"), new BigDecimal("10.00"), BigDecimal.ZERO,
                java.util.Optional.of(new BigDecimal("1.2500")), java.util.Optional.of(new BigDecimal("1.0000")));
        assertThrows(IllegalArgumentException.class, () -> EarnedValuePortfolioReport.fromNodes(List.of(node, usd)));
    }

    @Test
    void rejectsZeroPortfolioDenominator() {
        var zeroPv = new EarnedValueMetrics(tenant, structure, UUID.randomUUID(), baseline, date, "IDR",
                new BigDecimal("100.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("10.00"), new BigDecimal("-10.00"), new BigDecimal("0.00"),
                java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> EarnedValuePortfolioReport.fromNodes(List.of(zeroPv)));
    }

    private EarnedValueMetrics metrics(UUID node, String bac, String pv, String ev, String ac) {
        BigDecimal b = new BigDecimal(bac + ".00");
        BigDecimal p = new BigDecimal(pv + ".00");
        BigDecimal e = new BigDecimal(ev + ".00");
        BigDecimal a = new BigDecimal(ac + ".00");
        return new EarnedValueMetrics(tenant, structure, node, baseline, date, "IDR", b, p, e, a,
                e.subtract(a), e.subtract(p),
                java.util.Optional.of(e.divide(a, 4, java.math.RoundingMode.HALF_UP)),
                java.util.Optional.of(e.divide(p, 4, java.math.RoundingMode.HALF_UP)));
    }
}
