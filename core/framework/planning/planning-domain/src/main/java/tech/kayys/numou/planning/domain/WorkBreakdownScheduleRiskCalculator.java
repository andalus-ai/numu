package tech.kayys.numou.planning.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Projects dependency-aware schedule dates onto WBS nodes through explicit
 * tenant/structure/plan-scoped activity links. It never infers domain-specific
 * hierarchy or activity ownership.
 */
public final class WorkBreakdownScheduleRiskCalculator {
    public List<WorkBreakdownScheduleRisk> calculate(
            UUID tenantId, UUID structureId, PlanId planId,
            List<WorkBreakdownActivityLink> links,
            DependencyScheduleForecast scheduleForecast,
            List<ProgressForecast> progressForecasts) {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(structureId); Objects.requireNonNull(planId);
        Objects.requireNonNull(links); Objects.requireNonNull(scheduleForecast); Objects.requireNonNull(progressForecasts);
        Map<UUID, ProgressForecast> progressByNode = new HashMap<>();
        for (ProgressForecast progress : progressForecasts) {
            Objects.requireNonNull(progress, "Progress forecast cannot be null");
            if (!tenantId.equals(progress.tenantId()) || !structureId.equals(progress.structureId()))
                throw new IllegalArgumentException("Progress forecast belongs to a different tenant or structure");
            if (progressByNode.putIfAbsent(progress.nodeId(), progress) != null)
                throw new IllegalArgumentException("Multiple progress forecasts supplied for WBS node " + progress.nodeId());
        }

        Map<UUID, List<ActivityScheduleForecast>> byNode = new LinkedHashMap<>();
        Set<ActivityId> linkedActivities = new HashSet<>();
        for (WorkBreakdownActivityLink link : links) {
            Objects.requireNonNull(link, "Activity link cannot be null");
            if (!tenantId.equals(link.tenantId()) || !structureId.equals(link.structureId()) || !planId.equals(link.planId()))
                throw new IllegalArgumentException("Activity link belongs to a different tenant, structure, or plan");
            if (!linkedActivities.add(link.activityId()))
                throw new IllegalArgumentException("Activity may be linked to only one WBS node in this scope");
            ActivityScheduleForecast activity = scheduleForecast.activities().get(link.activityId());
            if (activity == null) throw new IllegalArgumentException("Linked activity is absent from schedule forecast");
            byNode.computeIfAbsent(link.nodeId(), ignored -> new ArrayList<>()).add(activity);
        }

        List<WorkBreakdownScheduleRisk> result = new ArrayList<>();
        for (Map.Entry<UUID, List<ActivityScheduleForecast>> entry : byNode.entrySet()) {
            List<ActivityScheduleForecast> activities = entry.getValue();
            activities.sort(Comparator.comparing(ActivityScheduleForecast::earliestStart)
                    .thenComparing(a -> a.activityId().value().toString()));
            LocalDate start = activities.stream().map(ActivityScheduleForecast::earliestStart).min(LocalDate::compareTo).orElseThrow();
            LocalDate finish = activities.stream().map(ActivityScheduleForecast::earliestFinish).max(LocalDate::compareTo).orElseThrow();
            int criticalCount = Math.toIntExact(activities.stream().filter(ActivityScheduleForecast::critical).count());
            result.add(new WorkBreakdownScheduleRisk(tenantId, structureId, entry.getKey(), planId,
                    activities.stream().map(ActivityScheduleForecast::activityId).toList(), criticalCount,
                    start, finish, Optional.ofNullable(progressByNode.remove(entry.getKey()))));
        }
        if (!progressByNode.isEmpty())
            throw new IllegalArgumentException("Progress forecast supplied for a WBS node without a linked schedule activity");
        result.sort(Comparator.comparing(WorkBreakdownScheduleRisk::forecastActivityFinish)
                .thenComparing(r -> r.nodeId().toString()));
        return List.copyOf(result);
    }
}
