package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PerformanceGoalId(UUID value) implements DomainId<UUID> {
    public PerformanceGoalId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PerformanceGoalId generate() {
        return new PerformanceGoalId(UUID.randomUUID());
    }

    public static PerformanceGoalId of(UUID value) {
        return new PerformanceGoalId(value);
    }
}
