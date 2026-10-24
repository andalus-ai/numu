package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectControlsReportTest {
    @Test
    void combinesScheduleAndProgressVarianceForSameReportingScope() {
        UUID tenant = UUID.randomUUID();
        UUID structure = UUID.randomUUID();
        UUID nodeBehind = UUID.randomUUID();
        UUID nodeAhead = UUID.randomUUID();
        UUID nodeOnPlan = UUID.randomUUID();
        PlanId plan = new PlanId(UUID.randomUUID());
        LocalDate date = LocalDate.parse("2026-10-24");
        LocalDate baselineFinish = date.plusDays(10);
        LocalDate forecastFinish = date.plusDays(14);

        ScheduleVarianceReport schedule = new ScheduleVarianceReport(
                tenant, structure, plan, UUID.randomUUID(), 3, 4, date,
                baselineFinish, forecastFinish, ChronoUnit.DAYS.between(baselineFinish, forecastFinish),
                List.of(), List.of());

        ProjectControlsReport report = ProjectControlsReport.of(
                tenant, structure, plan, date, schedule,
                List.of(
                        variance(tenant, structure, nodeBehind, date, "-10.00"),
                        variance(tenant, structure, nodeAhead, date, "4.00"),
                        variance(tenant, structure, nodeOnPlan, date, "0.00")));

        assertTrue(report.scheduleIsLate());
        assertTrue(report.hasProgressBehindBaseline());
        assertEquals(1, report.behindBaselineNodeCount());
        assertEquals(1, report.aheadOfBaselineNodeCount());
        assertEquals(1, report.onBaselineNodeCount());
        assertEquals(0, new BigDecimal("-2.00").compareTo(report.averageProgressVariancePercentagePoints()));
    }

    @Test
    void emptyProgressListIsExplicitlyRepresentedAsZeroAverageAndZeroCounts() {
        UUID tenant = UUID.randomUUID();
        UUID structure = UUID.randomUUID();
        PlanId plan = new PlanId(UUID.randomUUID());
        LocalDate date = LocalDate.parse("2026-10-24");
        ScheduleVarianceReport schedule = new ScheduleVarianceReport(
                tenant, structure, plan, UUID.randomUUID(), 1, 1, date,
                date.plusDays(3), date.plusDays(3), 0, List.of(), List.of());

        ProjectControlsReport report = ProjectControlsReport.of(tenant, structure, plan, date, schedule, List.of());

        assertEquals(0, report.averageProgressVariancePercentagePoints().compareTo(BigDecimal.ZERO));
        assertEquals(0, report.behindBaselineNodeCount());
        assertEquals(0, report.aheadOfBaselineNodeCount());
        assertEquals(0, report.onBaselineNodeCount());
    }

    @Test
    void rejectsProgressVarianceFromAnotherReportingDateOrStructure() {
        UUID tenant = UUID.randomUUID();
        UUID structure = UUID.randomUUID();
        PlanId plan = new PlanId(UUID.randomUUID());
        LocalDate date = LocalDate.parse("2026-10-24");
        ScheduleVarianceReport schedule = new ScheduleVarianceReport(
                tenant, structure, plan, UUID.randomUUID(), 1, 1, date,
                date.plusDays(3), date.plusDays(3), 0, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> ProjectControlsReport.of(
                tenant, structure, plan, date, schedule,
                List.of(variance(tenant, structure, UUID.randomUUID(), date.minusDays(1), "1.00"))));

        assertThrows(IllegalArgumentException.class, () -> ProjectControlsReport.of(
                tenant, structure, plan, date, schedule,
                List.of(variance(tenant, UUID.randomUUID(), UUID.randomUUID(), date, "1.00"))));
    }

    @Test
    void rejectsDuplicateWbsNodeAndInconsistentSummaryValues() {
        UUID tenant = UUID.randomUUID();
        UUID structure = UUID.randomUUID();
        UUID node = UUID.randomUUID();
        PlanId plan = new PlanId(UUID.randomUUID());
        LocalDate date = LocalDate.parse("2026-10-24");
        ScheduleVarianceReport schedule = new ScheduleVarianceReport(
                tenant, structure, plan, UUID.randomUUID(), 1, 1, date,
                date.plusDays(3), date.plusDays(3), 0, List.of(), List.of());
        ProgressVariance value = variance(tenant, structure, node, date, "-2.00");

        assertThrows(IllegalArgumentException.class, () -> ProjectControlsReport.of(
                tenant, structure, plan, date, schedule, List.of(value, value)));

        assertThrows(IllegalArgumentException.class, () -> new ProjectControlsReport(
                tenant, structure, plan, date, schedule, List.of(value),
                BigDecimal.ZERO, 1, 0, 0));
    }

    private static ProgressVariance variance(UUID tenant, UUID structure, UUID node, LocalDate date, String points) {
        BigDecimal baseline = new BigDecimal("50.00");
        BigDecimal delta = new BigDecimal(points);
        return new ProgressVariance(tenant, structure, node, UUID.randomUUID(), UUID.randomUUID(),
                date.minusDays(7), date, baseline, baseline.add(delta), delta);
    }
}
