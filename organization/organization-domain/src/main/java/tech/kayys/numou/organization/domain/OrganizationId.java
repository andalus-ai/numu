package tech.kayys.numou.organization.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

/** Strongly-typed identity for {@link Organization}. */
public record OrganizationId(UUID value) implements DomainId<UUID> {
    public static OrganizationId of(UUID value) { return new OrganizationId(value); }
    public static OrganizationId generate() { return new OrganizationId(UUID.randomUUID()); }
}
