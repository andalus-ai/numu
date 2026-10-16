package tech.kayys.numou.tenancy.application.command.membership;

import tech.kayys.numou.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record AcceptTenantMembershipCommand(
        TenantId tenantId,
        TenantMembershipId membershipId,
        String actor
) {}
