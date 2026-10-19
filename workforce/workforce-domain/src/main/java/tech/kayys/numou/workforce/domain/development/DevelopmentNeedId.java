package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Strongly-typed identity for a {@link DevelopmentNeed} aggregate.
 */
public record DevelopmentNeedId(UUID value) implements DomainId<UUID> {

    public DevelopmentNeedId {
        if (value == null) throw new IllegalArgumentException("DevelopmentNeedId value must not be null");
    }

    /** Factory method that generates a new random id. */
    public static DevelopmentNeedId generate() {
        return new DevelopmentNeedId(UUID.randomUUID());
    }

    /** Reconstruct from a known UUID string (e.g. from persistence). */
    public static DevelopmentNeedId of(String uuid) {
        return new DevelopmentNeedId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
