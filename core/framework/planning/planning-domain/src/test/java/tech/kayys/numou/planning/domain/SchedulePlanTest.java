package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchedulePlanTest {
    private static ScheduleActivity activity(String code, int start, int finish, WorkItemReference link) {
        return ScheduleActivity.create(code, code, LocalDate.of(2026, 1, start),
                LocalDate.of(2026, 1, finish), link, start);
    }

    @Test
    void capturesImmutableBaselineAndAllowsWorkingForecastToChange() {
        var plan = SchedulePlan.create("DELIVERY", "Delivery plan");
        var first = activity("A", 1, 3, new WorkItemReference("SCOPE_NODE", "scope-123"));
        plan.addActivity(first);
        var baseline = plan.createBaseline("Approved v1", Instant.parse("2026-01-01T00:00:00Z"), "planner");

        var shifted = new ScheduleActivity(first.id(), first.code(), first.name(),
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5), first.workItem(), first.position());
        plan.replaceActivity(shifted);

        assertEquals(LocalDate.of(2026, 1, 1), baseline.activities().get(0).plannedStart());
        assertEquals(LocalDate.of(2026, 1, 2), plan.activities().get(0).plannedStart());
        assertThrows(UnsupportedOperationException.class, () -> baseline.activities().add(first));
    }

    @Test
    void baselineSequenceIsAppendOnlyAndIncrements() {
        var plan = SchedulePlan.create("P", "Plan");
        plan.addActivity(activity("A", 1, 2, null));
        var first = plan.createBaseline("v1", Instant.EPOCH, "a");
        var second = plan.createBaseline("v2", Instant.EPOCH.plusSeconds(1), "b");
        assertEquals(1, first.sequence());
        assertEquals(2, second.sequence());
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void dependenciesMustStayInsidePlanAndMustBeAcyclic() {
        var plan = SchedulePlan.create("P", "Plan");
        var a = activity("A", 1, 2, null);
        var b = activity("B", 3, 4, null);
        var c = activity("C", 5, 6, null);
        plan.addActivity(a);
        plan.addActivity(b);
        plan.addActivity(c);
        plan.addDependency(new ActivityDependency(a.id(), b.id()));
        plan.addDependency(new ActivityDependency(b.id(), c.id()));
        assertThrows(IllegalArgumentException.class,
                () -> plan.addDependency(new ActivityDependency(c.id(), a.id())));
        assertEquals(2, plan.dependencies().size());
    }

    @Test
    void activityCodesAreUniqueAndDatesAreOrdered() {
        var plan = SchedulePlan.create("P", "Plan");
        plan.addActivity(activity("Build", 1, 2, null));
        assertThrows(IllegalArgumentException.class, () -> plan.addActivity(activity("BUILD", 3, 4, null)));
        assertThrows(IllegalArgumentException.class, () -> ScheduleActivity.create("X", "Bad dates",
                LocalDate.of(2026, 1, 4), LocalDate.of(2026, 1, 3), null, 0));
    }

    @Test
    void retiredPlanCannotBeChangedOrBaselined() {
        var plan = SchedulePlan.create("P", "Plan");
        plan.addActivity(activity("A", 1, 2, null));
        plan.activate();
        plan.retire();
        assertThrows(IllegalStateException.class, () -> plan.addActivity(activity("B", 3, 4, null)));
        assertThrows(IllegalStateException.class,
                () -> plan.createBaseline("late", Instant.EPOCH, "planner"));
    }
}
