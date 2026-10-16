package tech.kayys.syirkah.organization.domain;

import java.util.Objects;

/**
 * Lightweight cross-domain reference to a Legal Entity (config02.md §P4-12 #6).
 */
public record LegalEntityRef(
        OrganizationId organizationId
) {
    public LegalEntityRef {
        Objects.requireNonNull(organizationId, "organizationId cannot be null");
    }

    public static LegalEntityRef of(OrganizationId organizationId) {
        return new LegalEntityRef(organizationId);
    }
}
