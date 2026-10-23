package tech.kayys.numou.planning.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One deterministic, revision-scoped activity change and its downstream exposure. */
public record PlanChangeImpactFinding(UUID findingId, Code code, ActivityId activityId,
        Integer previousDurationDays, Integer currentDurationDays,
        List<ActivityId> affectedDownstreamActivities, boolean criticalBefore, boolean criticalAfter,
        String detail) {
    public PlanChangeImpactFinding {
        Objects.requireNonNull(findingId, "Finding id cannot be null");
        Objects.requireNonNull(code, "Change code cannot be null");
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        affectedDownstreamActivities = List.copyOf(Objects.requireNonNull(affectedDownstreamActivities));
        detail = Objects.requireNonNull(detail, "Finding detail cannot be null").trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("Finding detail cannot be blank");
        if (previousDurationDays != null && previousDurationDays < 0)
            throw new IllegalArgumentException("Previous duration cannot be negative");
        if (currentDurationDays != null && currentDurationDays < 0)
            throw new IllegalArgumentException("Current duration cannot be negative");
    }
    public enum Code { ACTIVITY_ADDED, ACTIVITY_REMOVED, DURATION_CHANGED, DEPENDENCIES_CHANGED }
}
