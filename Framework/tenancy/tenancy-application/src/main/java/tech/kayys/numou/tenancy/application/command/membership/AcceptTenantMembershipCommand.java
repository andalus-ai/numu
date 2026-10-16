package tech.kayys.syirkah.tenancy.application.command.membership;

import tech.kayys.syirkah.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record AcceptTenantMembershipCommand(
        TenantId tenantId,
        TenantMembershipId membershipId,
        String actor
) {}
