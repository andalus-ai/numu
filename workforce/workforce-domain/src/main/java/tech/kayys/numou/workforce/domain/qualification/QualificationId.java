package tech.kayys.numou.workforce.domain.qualification;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity value object for a {@link Qualification} aggregate.
 */
public record QualificationId(UUID value) implements DomainId<UUID> {

    public static QualificationId generate() {
        return new QualificationId(UUID.randomUUID());
    }

    public static QualificationId of(UUID value) {
        return new QualificationId(value);
    }

    public static QualificationId of(String value) {
        return new QualificationId(UUID.fromString(value));
    }
}
