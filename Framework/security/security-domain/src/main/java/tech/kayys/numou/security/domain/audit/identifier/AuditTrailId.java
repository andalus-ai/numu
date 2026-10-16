package tech.kayys.syirkah.security.domain.audit.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifier for audit trail entries.
 */
public record AuditTrailId(UUID value) implements DomainId<UUID>, Serializable {

    public AuditTrailId {
        Objects.requireNonNull(value, "AuditTrailId value cannot be null");
    }

    public static AuditTrailId of(UUID value) {
        return new AuditTrailId(value);
    }

    public static AuditTrailId generate() {
        return new AuditTrailId(UUID.randomUUID());
    }

    public static AuditTrailId fromString(String value) {
        return new AuditTrailId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AuditTrailId{" + value + "}";
    }
}
