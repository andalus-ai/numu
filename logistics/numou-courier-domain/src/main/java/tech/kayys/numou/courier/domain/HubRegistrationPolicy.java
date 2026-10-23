package tech.kayys.numou.courier.domain;

import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnit;
import tech.kayys.numou.organization.domain.OrganizationUnitKind;

import java.util.Objects;

/**
 * Cross-aggregate registration checks that must run before a hub is persisted.
 * The caller must load the unit through an authorized organization repository;
 * this policy then validates its relationship and operational eligibility.
 */
public final class HubRegistrationPolicy {
    private HubRegistrationPolicy() {
    }

    public static void requireEligibleBranch(OrganizationId organizationId, OrganizationUnit branch) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(branch, "branch must not be null");

        if (!organizationId.equals(branch.getOrganizationId())) {
            throw new IllegalArgumentException("branch does not belong to the supplied organization");
        }
        if (branch.getKind() != OrganizationUnitKind.BRANCH) {
            throw new IllegalArgumentException("hub must be attached to an organization unit of kind BRANCH");
        }
        if (!branch.isActive()) {
            throw new IllegalStateException("a dissolved organization unit cannot operate a hub");
        }
    }
}
