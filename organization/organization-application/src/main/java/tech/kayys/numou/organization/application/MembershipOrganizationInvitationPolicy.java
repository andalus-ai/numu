package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default invitation authorization policy backed by the tenant-scoped membership contract.
 * Only active owners and administrators may issue, resend, or revoke invitations.
 */
public final class MembershipOrganizationInvitationPolicy implements OrganizationInvitationPolicyPort {
    private final OrganizationMembershipRepository memberships;

    public MembershipOrganizationInvitationPolicy(OrganizationMembershipRepository memberships) {
        this.memberships = Objects.requireNonNull(memberships);
    }

    @Override
    public CompletionStage<Void> requireCanInvite(TenantId tenantId,
            OrganizationId organizationId, UserId actorId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(actorId, "actorId");
        return memberships.findByTenantAndOrganizationAndUser(tenantId, organizationId, actorId)
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.failedFuture(
                                new SecurityException("Active organization administrator membership required"));
                    }
                    OrganizationMembership membership = found.get();
                    boolean privileged = membership.role() == OrganizationMembership.Role.OWNER
                            || membership.role() == OrganizationMembership.Role.ADMIN;
                    if (!membership.isActive() || !privileged) {
                        return CompletableFuture.failedFuture(
                                new SecurityException("Active organization administrator membership required"));
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }
}
