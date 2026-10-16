package tech.kayys.syirkah.hris.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Training program identifier.
 */
public record TrainingId(UUID value) implements DomainId<UUID>, Serializable {

    public TrainingId {
        Objects.requireNonNull(value, "TrainingId value cannot be null");
    }

    public static TrainingId of(UUID value) {
        return new TrainingId(value);
    }

    public static TrainingId generate() {
        return new TrainingId(UUID.randomUUID());
    }

    public static TrainingId fromString(String value) {
        return new TrainingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "TrainingId{" + value + "}";
    }
}
