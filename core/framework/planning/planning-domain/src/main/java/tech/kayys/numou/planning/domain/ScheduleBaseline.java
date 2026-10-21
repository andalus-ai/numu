package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of the schedule at approval time.
 * Later edits to the working plan cannot mutate this baseline's values.
 */
public record ScheduleBaseline(
        UUID id,
        int sequence,
        String name,
        Instant createdAt,
        String createdBy,
        List<ScheduleActivity> activities,
        List<ActivityDependency> dependencies) {

    public ScheduleBaseline {
        Objects.requireNonNull(id, "Baseline id cannot be null");
        if (sequence < 1) throw new IllegalArgumentException("Baseline sequence must be positive");
        name = requireText(name, "Baseline name");
        Objects.requireNonNull(createdAt, "Baseline creation time cannot be null");
        createdBy = requireText(createdBy, "Baseline creator");
        activities = List.copyOf(Objects.requireNonNull(activities, "Activities cannot be null"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "Dependencies cannot be null"));
    }

    public static ScheduleBaseline snapshot(
            int sequence, String name, Instant createdAt, String createdBy,
            List<ScheduleActivity> activities, List<ActivityDependency> dependencies) {
        return new ScheduleBaseline(UUID.randomUUID(), sequence, name, createdAt, createdBy,
                activities, dependencies);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
