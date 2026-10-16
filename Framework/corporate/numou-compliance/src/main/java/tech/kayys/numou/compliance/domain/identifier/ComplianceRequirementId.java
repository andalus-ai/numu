package tech.kayys.syirkah.compliance.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Compliance requirement identifier.
 */
public record ComplianceRequirementId(UUID value) implements DomainId<UUID>, Serializable {

    public ComplianceRequirementId {
        Objects.requireNonNull(value, "ComplianceRequirementId value cannot be null");
    }

    public static ComplianceRequirementId of(UUID value) {
        return new ComplianceRequirementId(value);
    }

    public static ComplianceRequirementId generate() {
        return new ComplianceRequirementId(UUID.randomUUID());
    }

    public static ComplianceRequirementId fromString(String value) {
        return new ComplianceRequirementId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ComplianceRequirementId{" + value + "}";
    }
}
