package tech.kayys.numou.planning.domain;

import java.util.Objects;

/** A neutral schedule activity duration, expressed in calendar days. */
public record ScheduleActivityDuration(ActivityId activityId, int durationDays) {
    public ScheduleActivityDuration {
        Objects.requireNonNull(activityId, "Activity id cannot be null");
        if (durationDays < 0) throw new IllegalArgumentException("Duration cannot be negative");
    }
}
