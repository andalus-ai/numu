package tech.kayys.numou.foundation.domain.tenant;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal multi-tenant identifier primitive for the Numou platform.
 */
public record TenantId(UUID value) implements DomainId<UUID>, Serializable {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    /**
     * Alias of {@link #generate()}, retained so callers migrating from the
     * former {@code tenancy} copy of {@code TenantId} keep compiling.
     */
    public static TenantId newId() {
        return generate();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
