package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record AuditableEntityId(String value) {
    public AuditableEntityId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("AuditableEntityId must not be blank");
    }
    public static AuditableEntityId newId() {
        return new AuditableEntityId(UUID.randomUUID().toString());
    }
    public static AuditableEntityId of(String value) {
        return new AuditableEntityId(value);
    }
}
