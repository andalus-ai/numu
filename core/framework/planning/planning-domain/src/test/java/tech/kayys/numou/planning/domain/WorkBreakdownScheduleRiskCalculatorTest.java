package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkBreakdownScheduleRiskCalculatorTest {
    private final WorkBreakdownScheduleRiskCalculator calculator = new WorkBreakdownScheduleRiskCalculator();
    private final DependencyScheduleForecastCalculator scheduleCalculator = new DependencyScheduleForecastCalculator();
    private ActivityId activity(long id) { return new ActivityId(new UUID(0, id)); }

    @Test
    void mapsCriticalActivitiesAndScheduleDatesToWbsNodes() {
        UUID tenant=UUID.randomUUID(), structure=UUID.randomUUID(), nodeA=UUID.randomUUID(), nodeB=UUID.randomUUID();
        PlanId plan=new PlanId(UUID.randomUUID());
        ActivityId a=activity(1), b=activity(2), c=activity(3);
        var schedule=scheduleCalculator.calculate(LocalDate.of(2026,1,1),
                List.of(new ScheduleActivityDuration(a,2),new ScheduleActivityDuration(b,5),new ScheduleActivityDuration(c,1)),
                List.of(new ActivityDependency(a,c),new ActivityDependency(b,c)));
        var links=List.of(new WorkBreakdownActivityLink(tenant,structure,nodeA,plan,a),
                new WorkBreakdownActivityLink(tenant,structure,nodeB,plan,b),
                new WorkBreakdownActivityLink(tenant,structure,nodeB,plan,c));
        var risks=calculator.calculate(tenant,structure,plan,links,schedule,List.of());
        assertEquals(2,risks.size());
        var riskA=risks.stream().filter(r->r.nodeId().equals(nodeA)).findFirst().orElseThrow();
        var riskB=risks.stream().filter(r->r.nodeId().equals(nodeB)).findFirst().orElseThrow();
        assertFalse(riskA.hasCriticalExposure());
        assertTrue(riskB.hasCriticalExposure());
        assertEquals(2,riskB.activityIds().size());
        assertEquals(LocalDate.of(2026,1,7),riskB.forecastActivityFinish());
    }

    @Test
    void rejectsCrossTenantLinksAndUnmappedProgressForecasts() {
        UUID tenant=UUID.randomUUID(), structure=UUID.randomUUID(), node=UUID.randomUUID();
        PlanId plan=new PlanId(UUID.randomUUID()); ActivityId a=activity(1);
        var schedule=scheduleCalculator.calculate(LocalDate.of(2026,1,1),List.of(new ScheduleActivityDuration(a,2)),List.of());
        var badLink=new WorkBreakdownActivityLink(UUID.randomUUID(),structure,node,plan,a);
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(tenant,structure,plan,List.of(badLink),schedule,List.of()));
        var progress=new ProgressForecast(tenant,structure,UUID.randomUUID(),UUID.randomUUID(),
                LocalDate.of(2026,1,1),LocalDate.of(2026,1,10),LocalDate.of(2026,1,12),
                new java.math.BigDecimal("50"),new java.math.BigDecimal("5"),2,2,"test-v1");
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(tenant,structure,plan,
                List.of(new WorkBreakdownActivityLink(tenant,structure,node,plan,a)),schedule,List.of(progress)));
    }
}
