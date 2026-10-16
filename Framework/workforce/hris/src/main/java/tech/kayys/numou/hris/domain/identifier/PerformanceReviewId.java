package tech.kayys.syirkah.hris.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Performance review identifier.
 */
public record PerformanceReviewId(UUID value) implements DomainId<UUID>, Serializable {

    public PerformanceReviewId {
        Objects.requireNonNull(value, "PerformanceReviewId value cannot be null");
    }

    public static PerformanceReviewId of(UUID value) {
        return new PerformanceReviewId(value);
    }

    public static PerformanceReviewId generate() {
        return new PerformanceReviewId(UUID.randomUUID());
    }

    public static PerformanceReviewId fromString(String value) {
        return new PerformanceReviewId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "PerformanceReviewId{" + value + "}";
    }
}
