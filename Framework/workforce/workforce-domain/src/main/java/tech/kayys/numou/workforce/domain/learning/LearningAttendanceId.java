package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record LearningAttendanceId(UUID value) implements DomainId<UUID> {
    public LearningAttendanceId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static LearningAttendanceId generate() {
        return new LearningAttendanceId(UUID.randomUUID());
    }

    public static LearningAttendanceId of(UUID value) {
        return new LearningAttendanceId(value);
    }
}
