package tech.kayys.numou.organization.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

/** Strongly-typed identity for {@link OrganizationUnit}. */
public record OrganizationUnitId(UUID value) implements DomainId<UUID> {
    public static OrganizationUnitId of(UUID value) { return new OrganizationUnitId(value); }
    public static OrganizationUnitId generate() { return new OrganizationUnitId(UUID.randomUUID()); }
}
