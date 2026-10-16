package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Strongly-typed identity for a {@link DevelopmentPlan} aggregate.
 */
public record DevelopmentPlanId(UUID value) implements DomainId<UUID> {

    public DevelopmentPlanId {
        if (value == null) throw new IllegalArgumentException("DevelopmentPlanId value must not be null");
    }

    /** Factory method that generates a new random id. */
    public static DevelopmentPlanId generate() {
        return new DevelopmentPlanId(UUID.randomUUID());
    }

    /** Reconstruct from a known UUID string (e.g. from persistence). */
    public static DevelopmentPlanId of(String uuid) {
        return new DevelopmentPlanId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
