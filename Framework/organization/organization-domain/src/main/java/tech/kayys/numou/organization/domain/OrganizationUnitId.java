package tech.kayys.syirkah.organization.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.UUID;

/** Strongly-typed identity for {@link OrganizationUnit}. */
public record OrganizationUnitId(UUID value) implements DomainId<UUID> {
    public static OrganizationUnitId of(UUID value) { return new OrganizationUnitId(value); }
    public static OrganizationUnitId generate() { return new OrganizationUnitId(UUID.randomUUID()); }
}
