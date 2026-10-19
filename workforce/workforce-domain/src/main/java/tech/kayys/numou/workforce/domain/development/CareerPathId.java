package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Strongly-typed identity for a {@link CareerPath} aggregate.
 */
public record CareerPathId(UUID value) implements DomainId<UUID> {

    public CareerPathId {
        if (value == null) throw new IllegalArgumentException("CareerPathId value must not be null");
    }

    /** Factory method that generates a new random id. */
    public static CareerPathId generate() {
        return new CareerPathId(UUID.randomUUID());
    }

    /** Reconstruct from a known UUID string (e.g. from persistence). */
    public static CareerPathId of(String uuid) {
        return new CareerPathId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
