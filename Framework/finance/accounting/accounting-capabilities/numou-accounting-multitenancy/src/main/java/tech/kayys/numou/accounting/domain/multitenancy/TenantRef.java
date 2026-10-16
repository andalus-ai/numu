package tech.kayys.syirkah.accounting.domain.multitenancy;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant identifier for multi-tenant isolation across all accounting aggregates.
 */
public record TenantRef(String value) implements ValueObject {
    public TenantRef {
        Objects.requireNonNull(value, "TenantRef value cannot be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException("TenantRef cannot be blank");
        }
    }

    public static TenantRef of(String value) {
        return new TenantRef(value);
    }

    public static TenantRef generate() {
        return new TenantRef(UUID.randomUUID().toString());
    }

    public static TenantRef defaultTenant() {
        return new TenantRef("default");
    }
}
