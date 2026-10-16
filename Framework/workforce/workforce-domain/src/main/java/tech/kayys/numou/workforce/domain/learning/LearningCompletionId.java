package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningCompletionId(UUID value) implements DomainId<UUID> {
    public LearningCompletionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningCompletionId generate() {
        return new LearningCompletionId(UUID.randomUUID());
    }

    public static LearningCompletionId of(UUID value) {
        return new LearningCompletionId(value);
    }
}
