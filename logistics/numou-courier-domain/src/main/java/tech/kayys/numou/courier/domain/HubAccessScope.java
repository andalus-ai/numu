package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.util.Objects;
import java.util.Set;

/**
 * Request authorization scope resolved by the application/security layer.
 * This is a guard, not a substitute for repository/database tenant filtering.
 */
public record HubAccessScope(
        TenantId tenantId,
        Set<OrganizationUnitId> allowedBranches,
        Set<HubId> allowedHubs,
        boolean tenantAdministrator) {

    public HubAccessScope {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        allowedBranches = Set.copyOf(Objects.requireNonNull(allowedBranches, "allowedBranches must not be null"));
        allowedHubs = Set.copyOf(Objects.requireNonNull(allowedHubs, "allowedHubs must not be null"));
    }

    public void requireCanAccess(Hub hub) {
        Objects.requireNonNull(hub, "hub must not be null");
        requireTenant(hub.tenantId());
        if (!tenantAdministrator
                && !allowedHubs.contains(hub.id())
                && !allowedBranches.contains(hub.branchUnitId())) {
            throw new SecurityException("hub is outside the caller's branch and hub scope");
        }
    }

    public void requireCanCreateInBranch(TenantId requestedTenant, OrganizationUnitId branchUnitId) {
        Objects.requireNonNull(requestedTenant, "requestedTenant must not be null");
        Objects.requireNonNull(branchUnitId, "branchUnitId must not be null");
        requireTenant(requestedTenant);
        if (!tenantAdministrator && !allowedBranches.contains(branchUnitId)) {
            throw new SecurityException("branch is outside the caller's authorized scope");
        }
    }

    private void requireTenant(TenantId requestedTenant) {
        if (!tenantId.equals(requestedTenant)) {
            throw new SecurityException("cross-tenant hub access denied");
        }
    }
}
