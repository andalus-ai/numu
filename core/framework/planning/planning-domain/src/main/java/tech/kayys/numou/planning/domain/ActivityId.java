package tech.kayys.numou.planning.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an activity within a schedule plan. */
public record ActivityId(UUID value) {
    public ActivityId {
        Objects.requireNonNull(value, "Activity id cannot be null");
    }

    public static ActivityId generate() {
        return new ActivityId(UUID.randomUUID());
    }
}
