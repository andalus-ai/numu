package tech.kayys.syirkah.compliance.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Audit log entry identifier.
 */
public record AuditLogId(UUID value) implements DomainId<UUID>, Serializable {

    public AuditLogId {
        Objects.requireNonNull(value, "AuditLogId value cannot be null");
    }

    public static AuditLogId of(UUID value) {
        return new AuditLogId(value);
    }

    public static AuditLogId generate() {
        return new AuditLogId(UUID.randomUUID());
    }

    public static AuditLogId fromString(String value) {
        return new AuditLogId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AuditLogId{" + value + "}";
    }
}
