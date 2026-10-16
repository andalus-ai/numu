package tech.kayys.syirkah.tenancy.application.command.membership;

import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMemberType;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

public record InviteTenantMemberCommand(
        TenantId tenantId,
        UserRef user,
        TenantMemberType type,
        String actor
) {}
