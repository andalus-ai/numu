package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.user.UserId;

public interface MembershipManagementPort {
    Uni<Boolean> isActive(TenantId tenantId, UserId userId);
}
