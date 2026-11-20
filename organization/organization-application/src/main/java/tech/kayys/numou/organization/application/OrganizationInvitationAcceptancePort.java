package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import java.time.Instant;
import java.util.concurrent.CompletionStage;

/** Atomic persistence boundary for consuming an invitation and creating its membership. */
@FunctionalInterface
public interface OrganizationInvitationAcceptancePort {
    CompletionStage<AcceptedInvitation> accept(TenantId tenantId, String tokenHash,
            UserId authenticatedUserId, String verifiedEmail, Instant acceptedAt);

    record AcceptedInvitation(OrganizationInvitationId invitationId, OrganizationMembershipId membershipId,
            OrganizationId organizationId, UserId userId, OrganizationMembership.Role role) { }
}