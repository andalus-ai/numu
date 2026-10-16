package tech.kayys.syirkah.tenancy.domain.membership;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Strongly-typed TenantMembership identifier. */
public record TenantMembershipId(UUID value) implements DomainId<UUID>, Serializable {

    public TenantMembershipId {
        Objects.requireNonNull(value, "TenantMembershipId value must not be null");
    }

    public static TenantMembershipId newId() {
        return new TenantMembershipId(UUID.randomUUID());
    }

    public static TenantMembershipId of(UUID value) {
        return new TenantMembershipId(value);
    }

    @Override
    public String toString() { return value.toString(); }
}
