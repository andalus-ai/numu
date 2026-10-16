package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningOfferingId(UUID value) implements DomainId<UUID> {
    public LearningOfferingId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningOfferingId generate() {
        return new LearningOfferingId(UUID.randomUUID());
    }

    public static LearningOfferingId of(UUID value) {
        return new LearningOfferingId(value);
    }
}
