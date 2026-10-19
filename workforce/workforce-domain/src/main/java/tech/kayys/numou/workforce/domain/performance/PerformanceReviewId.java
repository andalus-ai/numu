package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PerformanceReviewId(UUID value) implements DomainId<UUID> {
    public PerformanceReviewId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PerformanceReviewId generate() {
        return new PerformanceReviewId(UUID.randomUUID());
    }

    public static PerformanceReviewId of(UUID value) {
        return new PerformanceReviewId(value);
    }
}
