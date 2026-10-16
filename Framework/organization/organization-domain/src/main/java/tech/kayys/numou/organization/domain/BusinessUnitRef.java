package tech.kayys.syirkah.organization.domain;

import java.util.Objects;

/**
 * Lightweight cross-domain reference to a Business Unit (config02.md §P4-12 #8, #9).
 */
public record BusinessUnitRef(
        OrganizationUnitId unitId,
        OrganizationId organizationId
) {
    public BusinessUnitRef {
        Objects.requireNonNull(unitId, "unitId cannot be null");
        Objects.requireNonNull(organizationId, "organizationId cannot be null");
    }

    public static BusinessUnitRef of(OrganizationUnitId unitId, OrganizationId organizationId) {
        return new BusinessUnitRef(unitId, organizationId);
    }
}
