package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;

import java.util.Objects;

/**
 * authenticatedUserId must be populated from the verified security principal,
 * not copied from a client-controlled request body.
 */
public record AcceptOrganizationInvitationCommand(
        TenantId tenantId,
        OrganizationMembershipId membershipId,
        UserId authenticatedUserId
) {
    public AcceptOrganizationInvitationCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(membershipId, "membershipId cannot be null");
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");
    }
}
