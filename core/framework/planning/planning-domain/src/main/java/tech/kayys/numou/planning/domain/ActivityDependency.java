package tech.kayys.numou.planning.domain;

import java.util.Objects;

/** Finish-to-start precedence link between activities in the same plan. */
public record ActivityDependency(ActivityId predecessor, ActivityId successor) {
    public ActivityDependency {
        Objects.requireNonNull(predecessor, "Predecessor cannot be null");
        Objects.requireNonNull(successor, "Successor cannot be null");
        if (predecessor.equals(successor)) {
            throw new IllegalArgumentException("An activity cannot depend on itself");
        }
    }
}
