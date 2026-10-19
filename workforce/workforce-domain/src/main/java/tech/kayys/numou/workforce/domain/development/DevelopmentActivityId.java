package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Strongly-typed identity for a {@link DevelopmentActivity} aggregate.
 */
public record DevelopmentActivityId(UUID value) implements DomainId<UUID> {

    public DevelopmentActivityId {
        if (value == null) throw new IllegalArgumentException("DevelopmentActivityId value must not be null");
    }

    /** Factory method that generates a new random id. */
    public static DevelopmentActivityId generate() {
        return new DevelopmentActivityId(UUID.randomUUID());
    }

    /** Reconstruct from a known UUID string (e.g. from persistence). */
    public static DevelopmentActivityId of(String uuid) {
        return new DevelopmentActivityId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
