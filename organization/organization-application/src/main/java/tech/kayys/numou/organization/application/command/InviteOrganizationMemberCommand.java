package tech.kayys.numou.organization.application.command;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;

import java.util.Objects;

public record InviteOrganizationMemberCommand(
        TenantId tenantId,
        OrganizationId organizationId,
        UserId userId
) {
    public InviteOrganizationMemberCommand {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(organizationId, "organizationId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}
