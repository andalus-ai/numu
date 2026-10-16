package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PerformanceFeedbackId(UUID value) implements DomainId<UUID> {
    public PerformanceFeedbackId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PerformanceFeedbackId generate() {
        return new PerformanceFeedbackId(UUID.randomUUID());
    }

    public static PerformanceFeedbackId of(UUID value) {
        return new PerformanceFeedbackId(value);
    }
}
