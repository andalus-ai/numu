package tech.kayys.numou.workforce.domain.compliance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

/**
 * Identity of a ComplianceAssessment aggregate.
 */
public record ComplianceAssessmentId(UUID value) implements DomainId<UUID> {

    public ComplianceAssessmentId {
        if (value == null) throw new IllegalArgumentException("ComplianceAssessmentId value must not be null");
    }

    /** Factory method to generate a new random identity. */
    public static ComplianceAssessmentId generate() {
        return new ComplianceAssessmentId(UUID.randomUUID());
    }

    /** Reconstruct from an existing UUID string. */
    public static ComplianceAssessmentId of(String uuid) {
        return new ComplianceAssessmentId(UUID.fromString(uuid));
    }

    /** Reconstruct from an existing UUID. */
    public static ComplianceAssessmentId of(UUID uuid) {
        return new ComplianceAssessmentId(uuid);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
