package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import java.util.concurrent.CompletionStage;

/** Host-provided authorization boundary; implementations must validate actor, tenant and organization. */
@FunctionalInterface
public interface OrganizationInvitationPolicyPort {
    CompletionStage<Void> requireCanInvite(TenantId tenantId, OrganizationId organizationId, UserId actorId);
}