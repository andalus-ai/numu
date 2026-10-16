package tech.kayys.syirkah.organization.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record OrganizationMembershipId(UUID value) implements DomainId<UUID> {
    public OrganizationMembershipId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static OrganizationMembershipId of(UUID value) {
        return new OrganizationMembershipId(value);
    }

    public static OrganizationMembershipId generate() {
        return new OrganizationMembershipId(UUID.randomUUID());
    }
}
