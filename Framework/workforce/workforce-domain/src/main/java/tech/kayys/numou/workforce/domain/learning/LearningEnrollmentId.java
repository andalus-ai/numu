package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningEnrollmentId(UUID value) implements DomainId<UUID> {
    public LearningEnrollmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningEnrollmentId generate() {
        return new LearningEnrollmentId(UUID.randomUUID());
    }

    public static LearningEnrollmentId of(UUID value) {
        return new LearningEnrollmentId(value);
    }
}
