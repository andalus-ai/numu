package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProgressForecastCalculatorTest {
    @Test
    void forecastsFinishAndReportsVarianceUsingLinearTrend() {
        UUID tenant=UUID.randomUUID(), structure=UUID.randomUUID(), node=UUID.randomUUID();
        var snapshots=List.of(
                snapshot(tenant,structure,node,"2026-10-01","20"),
                snapshot(tenant,structure,node,"2026-10-11","40"),
                snapshot(tenant,structure,node,"2026-10-21","60"));
        ProgressForecast result=new ProgressForecastCalculator().calculate(snapshots, LocalDate.parse("2026-11-10"));
        assertEquals(LocalDate.parse("2026-11-10"), result.forecastFinishDate());
        assertEquals(0, result.estimatedProgressPointsPerDay().compareTo(new BigDecimal("2.000000000000")));
        assertEquals(0, result.scheduleVarianceDays());
        assertEquals(3, result.observationsUsed());
    }

    @Test
    void rejectsMixedScopeAndNonPositiveTrend() {
        UUID tenant=UUID.randomUUID(), structure=UUID.randomUUID(), node=UUID.randomUUID();
        var a=snapshot(tenant,structure,node,"2026-10-01","50");
        var b=snapshot(tenant,structure,node,"2026-10-11","40");
        assertThrows(IllegalArgumentException.class, () -> new ProgressForecastCalculator().calculate(List.of(a,b), LocalDate.parse("2026-11-01")));
        var c=snapshot(tenant,structure,UUID.randomUUID(),"2026-10-11","60");
        assertThrows(IllegalArgumentException.class, () -> new ProgressForecastCalculator().calculate(List.of(a,c), LocalDate.parse("2026-11-01")));
    }

    @Test
    void requiresDistinctDatesAndIncompleteWork() {
        UUID tenant=UUID.randomUUID(), structure=UUID.randomUUID(), node=UUID.randomUUID();
        var a=snapshot(tenant,structure,node,"2026-10-01","50");
        var sameDate=snapshot(tenant,structure,node,"2026-10-01","60");
        assertThrows(IllegalArgumentException.class, () -> new ProgressForecastCalculator().calculate(List.of(a,sameDate), LocalDate.parse("2026-11-01")));
        var complete=snapshot(tenant,structure,node,"2026-10-11","100");
        assertThrows(IllegalArgumentException.class, () -> new ProgressForecastCalculator().calculate(List.of(a,complete), LocalDate.parse("2026-11-01")));
    }

    private static WorkBreakdownProgressSnapshot snapshot(UUID tenant, UUID structure, UUID node, String date, String pct) {
        BigDecimal p=new BigDecimal(pct);
        return new WorkBreakdownProgressSnapshot(UUID.randomUUID(),tenant,structure,node,UUID.randomUUID(),1,
                LocalDate.parse(date),"leaf-weight-v1",p,BigDecimal.ONE,1,
                Instant.parse(date+"T10:00:00Z"),
                List.of(new WorkBreakdownProgressSnapshot.ActivityContribution(UUID.randomUUID(),p,BigDecimal.ONE)));
    }
}
