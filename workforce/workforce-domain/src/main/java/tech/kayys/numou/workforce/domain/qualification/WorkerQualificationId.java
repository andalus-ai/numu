package tech.kayys.numou.workforce.domain.qualification;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity value object for a {@link WorkerQualification} aggregate.
 */
public record WorkerQualificationId(UUID value) implements DomainId<UUID> {

    public static WorkerQualificationId generate() {
        return new WorkerQualificationId(UUID.randomUUID());
    }

    public static WorkerQualificationId of(UUID value) {
        return new WorkerQualificationId(value);
    }

    public static WorkerQualificationId of(String value) {
        return new WorkerQualificationId(UUID.fromString(value));
    }
}
