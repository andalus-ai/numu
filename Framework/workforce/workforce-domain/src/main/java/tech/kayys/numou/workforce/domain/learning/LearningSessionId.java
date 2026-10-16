package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningSessionId(UUID value) implements DomainId<UUID> {
    public LearningSessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningSessionId generate() {
        return new LearningSessionId(UUID.randomUUID());
    }

    public static LearningSessionId of(UUID value) {
        return new LearningSessionId(value);
    }
}
