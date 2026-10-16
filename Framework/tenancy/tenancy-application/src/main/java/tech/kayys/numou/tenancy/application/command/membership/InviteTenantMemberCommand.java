package tech.kayys.numou.tenancy.application.command.membership;

import tech.kayys.numou.foundation.domain.ref.UserRef;
import tech.kayys.numou.tenancy.domain.membership.TenantMemberType;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record InviteTenantMemberCommand(
        TenantId tenantId,
        UserRef user,
        TenantMemberType type,
        String actor
) {}
