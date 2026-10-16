package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record SuccessionReadinessAssessmentId(UUID value) implements DomainId<UUID> {
    public SuccessionReadinessAssessmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SuccessionReadinessAssessmentId generate() {
        return new SuccessionReadinessAssessmentId(UUID.randomUUID());
    }

    public static SuccessionReadinessAssessmentId of(UUID value) {
        return new SuccessionReadinessAssessmentId(value);
    }
}
