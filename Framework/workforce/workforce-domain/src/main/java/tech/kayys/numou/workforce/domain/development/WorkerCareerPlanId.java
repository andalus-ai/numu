package tech.kayys.numou.workforce.domain.development;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Strongly-typed identity for a {@link WorkerCareerPlan} aggregate.
 */
public record WorkerCareerPlanId(UUID value) implements DomainId<UUID> {

    public WorkerCareerPlanId {
        if (value == null) throw new IllegalArgumentException("WorkerCareerPlanId value must not be null");
    }

    /** Factory method that generates a new random id. */
    public static WorkerCareerPlanId generate() {
        return new WorkerCareerPlanId(UUID.randomUUID());
    }

    /** Reconstruct from a known UUID string (e.g. from persistence). */
    public static WorkerCareerPlanId of(String uuid) {
        return new WorkerCareerPlanId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
