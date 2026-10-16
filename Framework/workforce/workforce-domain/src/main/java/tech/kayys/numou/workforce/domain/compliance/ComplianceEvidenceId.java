package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity of a ComplianceEvidence aggregate.
 */
public record ComplianceEvidenceId(UUID value) implements DomainId<UUID> {

    public ComplianceEvidenceId {
        if (value == null) throw new IllegalArgumentException("ComplianceEvidenceId value must not be null");
    }

    /** Factory method to generate a new random identity. */
    public static ComplianceEvidenceId generate() {
        return new ComplianceEvidenceId(UUID.randomUUID());
    }

    /** Reconstruct from an existing UUID string. */
    public static ComplianceEvidenceId of(String uuid) {
        return new ComplianceEvidenceId(UUID.fromString(uuid));
    }

    /** Reconstruct from an existing UUID. */
    public static ComplianceEvidenceId of(UUID uuid) {
        return new ComplianceEvidenceId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
