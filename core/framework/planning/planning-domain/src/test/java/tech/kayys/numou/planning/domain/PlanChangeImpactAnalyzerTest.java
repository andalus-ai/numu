package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanChangeImpactAnalyzerTest {
    private final PlanChangeImpactAnalyzer analyzer = new PlanChangeImpactAnalyzer();
    private final UUID tenant = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final UUID structure = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private final PlanId plan = new PlanId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
    private final LocalDate start = LocalDate.of(2026, 1, 1);
    private final ActivityId a = activity(1), b = activity(2), c = activity(3);

    private ActivityId activity(long value) { return new ActivityId(new UUID(0, value)); }

    private PlanRevisionSnapshot snapshot(long revision, int aDays, List<ActivityDependency> dependencies,
            Map<ActivityId, Integer> durations) {
        return new PlanRevisionSnapshot(tenant, structure, plan, revision,
                Instant.parse(revision == 1 ? "2026-01-01T00:00:00Z" : "2026-01-02T00:00:00Z"),
                start, durations, dependencies);
    }

    @Test
    void durationChangeTracesTransitiveDownstreamAndForecastFinishVariance() {
        var previous = snapshot(1, 2, List.of(new ActivityDependency(a, b), new ActivityDependency(b, c)),
                Map.of(a, 2, b, 3, c, 1));
        var current = snapshot(2, 4, List.of(new ActivityDependency(a, b), new ActivityDependency(b, c)),
                Map.of(a, 4, b, 3, c, 1));

        var report = analyzer.analyze(previous, current);

        assertEquals(1, report.findings().size());
        var finding = report.findings().get(0);
        assertEquals(PlanChangeImpactFinding.Code.DURATION_CHANGED, finding.code());
        assertEquals(a, finding.activityId());
        assertEquals(List.of(b, c), finding.affectedDownstreamActivities());
        assertEquals(2, report.forecastFinishVarianceDays());
        assertEquals(1, report.previousRevision());
        assertEquals(2, report.currentRevision());
        assertEquals(finding.findingId(), analyzer.analyze(previous, current).findings().get(0).findingId());
    }

    @Test
    void reportsAddedRemovedActivitiesAndChangedPredecessors() {
        var previous = snapshot(1, 2, List.of(new ActivityDependency(a, b), new ActivityDependency(b, c)),
                Map.of(a, 2, b, 3, c, 1));
        var current = snapshot(2, 2, List.of(new ActivityDependency(a, c)),
                Map.of(a, 2, c, 1, activity(4), 2));

        var report = analyzer.analyze(previous, current);

        assertTrue(report.findings().stream().anyMatch(f ->
                f.code() == PlanChangeImpactFinding.Code.ACTIVITY_REMOVED && f.activityId().equals(b)));
        assertTrue(report.findings().stream().anyMatch(f ->
                f.code() == PlanChangeImpactFinding.Code.ACTIVITY_ADDED && f.activityId().equals(activity(4))));
        assertTrue(report.findings().stream().anyMatch(f ->
                f.code() == PlanChangeImpactFinding.Code.DEPENDENCIES_CHANGED && f.activityId().equals(c)));
        var removed = report.findings().stream().filter(f ->
                f.code() == PlanChangeImpactFinding.Code.ACTIVITY_REMOVED).findFirst().orElseThrow();
        assertEquals(List.of(c), removed.affectedDownstreamActivities());
    }

    @Test
    void unchangedRevisionContentsHaveNoFindingsAndScopeIsEnforced() {
        var previous = snapshot(1, 2, List.of(new ActivityDependency(a, b)), Map.of(a, 2, b, 1));
        var current = snapshot(2, 2, List.of(new ActivityDependency(a, b)), Map.of(a, 2, b, 1));
        assertTrue(analyzer.analyze(previous, current).findings().isEmpty());

        var foreign = new PlanRevisionSnapshot(UUID.randomUUID(), structure, plan, 2,
                Instant.parse("2026-01-02T00:00:00Z"), start, Map.of(a, 2, b, 1),
                List.of(new ActivityDependency(a, b)));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(previous, foreign));
    }

    @Test
    void rejectsRevisionRegressionAndInvalidDependencyGraphs() {
        var previous = snapshot(2, 2, List.of(), Map.of(a, 2));
        var older = snapshot(1, 2, List.of(), Map.of(a, 2));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(previous, older));

        var cyclic = snapshot(3, 2, List.of(new ActivityDependency(a, b), new ActivityDependency(b, a)),
                Map.of(a, 2, b, 1));
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyze(previous, cyclic));
    }
}
