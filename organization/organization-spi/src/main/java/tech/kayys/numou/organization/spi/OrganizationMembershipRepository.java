package tech.kayys.numou.organization.spi;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Persistence port for tenant-owned organization memberships. */
public interface OrganizationMembershipRepository
        extends Repository<OrganizationMembership, OrganizationMembershipId> {

    /** Legacy unscoped lookup; tenant-facing application paths must use scoped methods. */
    @Deprecated(forRemoval = false)
    CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId);

    CompletionStage<Boolean> existsByTenantAndId(TenantId tenantId, OrganizationMembershipId membershipId);

    CompletionStage<Optional<OrganizationMembership>> findByTenantAndId(
            TenantId tenantId, OrganizationMembershipId membershipId);

    CompletionStage<Optional<OrganizationMembership>> findByTenantAndOrganizationAndUser(
            TenantId tenantId, OrganizationId organizationId, UserId userId);

    CompletionStage<OrganizationMembership> save(OrganizationMembership membership, TenantId tenantId);
}
