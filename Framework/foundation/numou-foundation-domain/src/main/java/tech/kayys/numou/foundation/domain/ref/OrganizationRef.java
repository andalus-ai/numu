package tech.kayys.numou.foundation.domain.ref;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Universal cross-domain lightweight reference to an Organization.
 * Tenancy stores this instead of the full Organization aggregate so
 * it never imports the Party/Organization module as a hard dependency.
 */
public record OrganizationRef(UUID value) implements DomainId<UUID>, Serializable {

    public OrganizationRef {
        Objects.requireNonNull(value, "OrganizationRef value must not be null");
    }

    public static OrganizationRef of(UUID value) {
        return new OrganizationRef(value);
    }

    public static OrganizationRef of(String value) {
        return new OrganizationRef(UUID.fromString(value));
    }

    public static OrganizationRef generate() {
        return new OrganizationRef(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
