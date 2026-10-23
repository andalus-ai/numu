package tech.kayys.numou.planning.domain;

import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Compares plan revisions and traces schedule exposure through both dependency graphs. */
public final class PlanChangeImpactAnalyzer {
    private final DependencyScheduleForecastCalculator forecastCalculator;
    public PlanChangeImpactAnalyzer() { this(new DependencyScheduleForecastCalculator()); }
    public PlanChangeImpactAnalyzer(DependencyScheduleForecastCalculator forecastCalculator) {
        this.forecastCalculator = Objects.requireNonNull(forecastCalculator);
    }

    public PlanChangeImpactReport analyze(PlanRevisionSnapshot previous, PlanRevisionSnapshot current) {
        Objects.requireNonNull(previous, "Previous revision cannot be null");
        Objects.requireNonNull(current, "Current revision cannot be null");
        if (!previous.tenantId().equals(current.tenantId())
                || !previous.structureId().equals(current.structureId())
                || !previous.planId().equals(current.planId()))
            throw new IllegalArgumentException("Revisions must belong to the same tenant, structure, and plan");
        if (current.revision() <= previous.revision())
            throw new IllegalArgumentException("Current revision must be greater than previous revision");
        if (current.capturedAt().isBefore(previous.capturedAt()))
            throw new IllegalArgumentException("Current revision capture time cannot precede previous capture time");
        if (!previous.projectStart().equals(current.projectStart()))
            throw new IllegalArgumentException("Project start must match to compare schedule change impact");

        DependencyScheduleForecast before = forecastCalculator.calculate(previous.projectStart(),
                durations(previous), previous.dependencies());
        DependencyScheduleForecast after = forecastCalculator.calculate(current.projectStart(),
                durations(current), current.dependencies());
        Set<ActivityId> oldCritical = new HashSet<>(before.criticalPath());
        Set<ActivityId> newCritical = new HashSet<>(after.criticalPath());
        List<ActivityId> newlyCritical = sortedDifference(newCritical, oldCritical);
        List<ActivityId> noLongerCritical = sortedDifference(oldCritical, newCritical);
        Map<ActivityId, Set<ActivityId>> oldPredecessors = predecessors(previous);
        Map<ActivityId, Set<ActivityId>> newPredecessors = predecessors(current);
        Map<ActivityId, Set<ActivityId>> graph = successorGraph(previous, current);
        Set<ActivityId> allIds = new HashSet<>(previous.activityDurationsDays().keySet());
        allIds.addAll(current.activityDurationsDays().keySet());
        List<ActivityId> orderedIds = allIds.stream().sorted(activityOrder()).toList();
        List<Change> changes = new ArrayList<>();
        for (ActivityId id : orderedIds) {
            boolean inOld = previous.activityDurationsDays().containsKey(id);
            boolean inNew = current.activityDurationsDays().containsKey(id);
            if (!inOld) {
                changes.add(new Change(PlanChangeImpactFinding.Code.ACTIVITY_ADDED, id, null,
                        current.activityDurationsDays().get(id), "Activity was added in the current revision."));
                continue;
            }
            if (!inNew) {
                changes.add(new Change(PlanChangeImpactFinding.Code.ACTIVITY_REMOVED, id,
                        previous.activityDurationsDays().get(id), null, "Activity was removed from the current revision."));
                continue;
            }
            Integer oldDuration = previous.activityDurationsDays().get(id);
            Integer newDuration = current.activityDurationsDays().get(id);
            if (!oldDuration.equals(newDuration))
                changes.add(new Change(PlanChangeImpactFinding.Code.DURATION_CHANGED, id, oldDuration,
                        newDuration, "Activity duration changed between plan revisions."));
            if (!oldPredecessors.getOrDefault(id, Set.of()).equals(newPredecessors.getOrDefault(id, Set.of())))
                changes.add(new Change(PlanChangeImpactFinding.Code.DEPENDENCIES_CHANGED, id, oldDuration,
                        newDuration, "Activity predecessor set changed between plan revisions."));
        }

        List<PlanChangeImpactFinding> findings = new ArrayList<>();
        for (Change change : changes) {
            ActivityScheduleForecast oldForecast = before.activities().get(change.activityId());
            ActivityScheduleForecast newForecast = after.activities().get(change.activityId());
            Set<ActivityId> downstream = downstream(change.activityId(), graph);
            downstream.remove(change.activityId());
            List<ActivityId> affected = downstream.stream()
                    .filter(id -> previous.activityDurationsDays().containsKey(id)
                            || current.activityDurationsDays().containsKey(id))
                    .sorted(activityOrder()).toList();
            findings.add(new PlanChangeImpactFinding(
                    stableFindingId(previous, current, change.code(), change.activityId()),
                    change.code(), change.activityId(), change.previousDuration(), change.currentDuration(),
                    affected, oldForecast != null && oldForecast.critical(),
                    newForecast != null && newForecast.critical(), change.detail()));
        }
        findings.sort(Comparator.comparing((PlanChangeImpactFinding f) -> f.activityId().value().toString())
                .thenComparing(f -> f.code().name()));
        return new PlanChangeImpactReport(previous.tenantId(), previous.structureId(), previous.planId(),
                previous.revision(), current.revision(), previous.capturedAt(), current.capturedAt(),
                previous.projectStart(), before.forecastFinish(), after.forecastFinish(),
                ChronoUnit.DAYS.between(before.forecastFinish(), after.forecastFinish()),
                before.criticalPath(), after.criticalPath(), newlyCritical, noLongerCritical, findings,
                DependencyScheduleForecastCalculator.METHOD_VERSION);
    }

    private static List<ScheduleActivityDuration> durations(PlanRevisionSnapshot snapshot) {
        return snapshot.activityDurationsDays().entrySet().stream()
                .map(e -> new ScheduleActivityDuration(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(e -> e.activityId().value().toString())).toList();
    }
    private static Map<ActivityId, Set<ActivityId>> predecessors(PlanRevisionSnapshot snapshot) {
        Map<ActivityId, Set<ActivityId>> result = new HashMap<>();
        for (ActivityDependency dependency : snapshot.dependencies())
            result.computeIfAbsent(dependency.successor(), ignored -> new HashSet<>()).add(dependency.predecessor());
        return result;
    }
    private static Map<ActivityId, Set<ActivityId>> successorGraph(
            PlanRevisionSnapshot previous, PlanRevisionSnapshot current) {
        Map<ActivityId, Set<ActivityId>> graph = new HashMap<>();
        for (ActivityDependency dependency : previous.dependencies())
            graph.computeIfAbsent(dependency.predecessor(), ignored -> new HashSet<>()).add(dependency.successor());
        for (ActivityDependency dependency : current.dependencies())
            graph.computeIfAbsent(dependency.predecessor(), ignored -> new HashSet<>()).add(dependency.successor());
        return graph;
    }
    private static Set<ActivityId> downstream(ActivityId root, Map<ActivityId, Set<ActivityId>> graph) {
        Set<ActivityId> visited = new HashSet<>();
        ArrayDeque<ActivityId> queue = new ArrayDeque<>(graph.getOrDefault(root, Set.of()));
        while (!queue.isEmpty()) {
            ActivityId next = queue.removeFirst();
            if (visited.add(next)) queue.addAll(graph.getOrDefault(next, Set.of()));
        }
        return visited;
    }
    private static List<ActivityId> sortedDifference(Set<ActivityId> left, Set<ActivityId> right) {
        return left.stream().filter(id -> !right.contains(id)).sorted(activityOrder()).toList();
    }
    private static Comparator<ActivityId> activityOrder() {
        return Comparator.comparing(id -> id.value().toString());
    }
    private static UUID stableFindingId(PlanRevisionSnapshot previous, PlanRevisionSnapshot current,
            PlanChangeImpactFinding.Code code, ActivityId activityId) {
        String key = previous.tenantId() + "|" + previous.structureId() + "|" + previous.planId()
                + "|" + previous.revision() + "|" + current.revision() + "|" + code + "|" + activityId.value();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
    private record Change(PlanChangeImpactFinding.Code code, ActivityId activityId,
            Integer previousDuration, Integer currentDuration, String detail) { }
}
