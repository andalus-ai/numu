package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable, domain-neutral input snapshot for comparing two explicit plan revisions. */
public record PlanRevisionSnapshot(UUID tenantId, UUID structureId, PlanId planId, long revision,
        Instant capturedAt, LocalDate projectStart, Map<ActivityId, Integer> activityDurationsDays,
        List<ActivityDependency> dependencies) {
    public PlanRevisionSnapshot {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(capturedAt, "Capture timestamp cannot be null");
        Objects.requireNonNull(projectStart, "Project start cannot be null");
        Objects.requireNonNull(activityDurationsDays, "Activity durations cannot be null");
        Objects.requireNonNull(dependencies, "Dependencies cannot be null");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        activityDurationsDays = Map.copyOf(activityDurationsDays);
        dependencies = List.copyOf(dependencies);
        if (activityDurationsDays.isEmpty()) throw new IllegalArgumentException("A revision must contain activities");
        activityDurationsDays.forEach((id, duration) -> {
            Objects.requireNonNull(id, "Activity id cannot be null");
            Objects.requireNonNull(duration, "Activity duration cannot be null");
            if (duration < 0) throw new IllegalArgumentException("Activity duration cannot be negative");
        });
        Set<String> uniqueDependencies = new HashSet<>();
        for (ActivityDependency dependency : dependencies) {
            Objects.requireNonNull(dependency, "Dependency cannot be null");
            if (!activityDurationsDays.containsKey(dependency.predecessor())
                    || !activityDurationsDays.containsKey(dependency.successor())) {
                throw new IllegalArgumentException("Dependency references an activity outside this revision");
            }
            String key = dependency.predecessor().value() + "->" + dependency.successor().value();
            if (!uniqueDependencies.add(key)) throw new IllegalArgumentException("Duplicate dependency: " + key);
        }
    }
}
