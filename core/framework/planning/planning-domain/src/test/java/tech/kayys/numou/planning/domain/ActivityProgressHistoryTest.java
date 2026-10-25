package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ActivityProgressHistoryTest {
    private final ActivityId activityId = ActivityId.generate();

    private ActivityProgressObservation observation(String key, int day, String percent) {
        return ActivityProgressObservation.record(activityId, LocalDate.of(2026, 5, day),
                new BigDecimal(percent), Instant.parse("2026-05-20T10:00:00Z"), "site-lead", key, "daily-report-1");
    }

    @Test
    void progressIsValidatedAndStoredAsImmutableDatedEvidence() {
        var history = new ActivityProgressHistory(PlanId.generate());
        var first = observation("report-1", 10, "25.00");
        assertTrue(history.append(first));
        assertFalse(history.append(observation("report-1", 10, "25")));
        assertEquals(1, history.observations().size());
        assertEquals(new BigDecimal("25"), history.observations().get(0).percentComplete());
        assertThrows(IllegalArgumentException.class,
                () -> history.append(observation("report-1", 10, "30")));
        assertThrows(IllegalArgumentException.class,
                () -> ActivityProgressObservation.record(activityId, LocalDate.now(),
                        new BigDecimal("100.01"), Instant.now(), "x", "bad", null));
    }

    @Test
    void latestObservationIsSelectedByBusinessDateNotInsertionOrder() {
        var history = new ActivityProgressHistory(PlanId.generate());
        history.append(observation("later", 18, "70"));
        history.append(observation("earlier", 12, "40"));
        assertEquals(0, history.latestAsOf(activityId, LocalDate.of(2026, 5, 15))
                .orElseThrow().percentComplete().compareTo(new BigDecimal("40")));
        assertEquals(0, history.latestAsOf(activityId, LocalDate.of(2026, 5, 20))
                .orElseThrow().percentComplete().compareTo(new BigDecimal("70")));
    }

    @Test
    void assessmentComparesActualToLinearInclusiveCalendarPlan() {
        var activity = ScheduleActivity.create("DEV-1", "Implement API",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10),
                new WorkItemReference("SCOPE_NODE", "scope-1"), 0);
        var assessment = ProgressAssessment.assess(activity, LocalDate.of(2026, 5, 5), new BigDecimal("40"));
        assertEquals(new BigDecimal("50.00"), assessment.plannedPercent());
        assertEquals(new BigDecimal("-10.00"), assessment.variancePercentagePoints());
        assertEquals(ProgressAssessment.Status.BEHIND, assessment.status());
        assertEquals(new BigDecimal("0.00"),
                ProgressAssessment.plannedPercent(activity, LocalDate.of(2026, 4, 30)));
        assertEquals(new BigDecimal("100.00"),
                ProgressAssessment.plannedPercent(activity, LocalDate.of(2026, 5, 10)));
    }
}
