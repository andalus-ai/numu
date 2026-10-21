package tech.kayys.numou.planning.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a schedule plan. */
public record PlanId(UUID value) {
    public PlanId {
        Objects.requireNonNull(value, "Plan id cannot be null");
    }

    public static PlanId generate() {
        return new PlanId(UUID.randomUUID());
    }
}
