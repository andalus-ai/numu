package tech.kayys.numou.planning.domain;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ScheduleVarianceCalculatorTest {
    private final ScheduleVarianceCalculator calculator = new ScheduleVarianceCalculator();
    private ActivityId activity(long id) { return new ActivityId(new UUID(0, id)); }
    @Test
    void comparesBaselineAndForecastAndReportsActivitySetDrift() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID();
        PlanId plan = PlanId.generate();
        ActivityId a = activity(1), b = activity(2), added = activity(3);
        var baseline = new ApprovedScheduleBaseline(UUID.randomUUID(), tenant, structure, plan, 2,
            Instant.parse("2026-01-01T00:00:00Z"), LocalDate.of(2026,1,1), LocalDate.of(2026,1,8),
            Map.of(a, new ApprovedScheduleBaseline.ActivityDates(LocalDate.of(2026,1,1), LocalDate.of(2026,1,3)),
                   b, new ApprovedScheduleBaseline.ActivityDates(LocalDate.of(2026,1,3), LocalDate.of(2026,1,8))));
        var forecast = new DependencyScheduleForecast(LocalDate.of(2026,1,1), LocalDate.of(2026,1,10), 9,
            List.of(a), Map.of(
                a, new ActivityScheduleForecast(a, LocalDate.of(2026,1,2), LocalDate.of(2026,1,4),
                    LocalDate.of(2026,1,2), LocalDate.of(2026,1,4), 0, true),
                added, new ActivityScheduleForecast(added, LocalDate.of(2026,1,4), LocalDate.of(2026,1,10),
                    LocalDate.of(2026,1,4), LocalDate.of(2026,1,10), 0, true)), "critical-path-v1");
        var report = calculator.calculate(tenant, structure, plan, 3, LocalDate.of(2026,1,10), baseline, forecast);
        assertEquals(2, report.projectFinishVarianceDays());
        assertTrue(report.projectFinishIsLate());
        assertEquals(1, report.activities().size());
        assertEquals(1, report.activities().get(0).startVarianceDays());
        assertEquals(1, report.activities().get(0).finishVarianceDays());
        assertEquals(1, report.findings().stream().filter(f ->
            f.code() == ScheduleVarianceFinding.Code.BASELINE_ACTIVITY_MISSING_FROM_FORECAST).count());
        assertEquals(1, report.findings().stream().filter(f ->
            f.code() == ScheduleVarianceFinding.Code.FORECAST_ACTIVITY_MISSING_FROM_BASELINE).count());
    }
    @Test
    void rejectsCrossScopeBaselineAndRevisionRegression() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID();
        PlanId plan = PlanId.generate(); ActivityId a = activity(1);
        var baseline = new ApprovedScheduleBaseline(UUID.randomUUID(), tenant, structure, plan, 4,
            Instant.parse("2026-01-01T00:00:00Z"), LocalDate.of(2026,1,1), LocalDate.of(2026,1,3),
            Map.of(a, new ApprovedScheduleBaseline.ActivityDates(LocalDate.of(2026,1,1), LocalDate.of(2026,1,3))));
        var forecast = new DependencyScheduleForecast(LocalDate.of(2026,1,1), LocalDate.of(2026,1,3), 2,
            List.of(a), Map.of(a, new ActivityScheduleForecast(a, LocalDate.of(2026,1,1),
                LocalDate.of(2026,1,3), LocalDate.of(2026,1,1), LocalDate.of(2026,1,3), 0, true)), "critical-path-v1");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(
            UUID.randomUUID(), structure, plan, 5, LocalDate.of(2026,1,3), baseline, forecast));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(
            tenant, structure, plan, 3, LocalDate.of(2026,1,3), baseline, forecast));
    }
}
