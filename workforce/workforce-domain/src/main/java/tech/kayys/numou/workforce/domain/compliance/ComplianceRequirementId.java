package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity of a ComplianceRequirement aggregate.
 */
public record ComplianceRequirementId(UUID value) implements DomainId<UUID> {

    public ComplianceRequirementId {
        if (value == null) throw new IllegalArgumentException("ComplianceRequirementId value must not be null");
    }

    /** Factory method to generate a new random identity. */
    public static ComplianceRequirementId generate() {
        return new ComplianceRequirementId(UUID.randomUUID());
    }

    /** Reconstruct from an existing UUID string. */
    public static ComplianceRequirementId of(String uuid) {
        return new ComplianceRequirementId(UUID.fromString(uuid));
    }

    /** Reconstruct from an existing UUID. */
    public static ComplianceRequirementId of(UUID uuid) {
        return new ComplianceRequirementId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
