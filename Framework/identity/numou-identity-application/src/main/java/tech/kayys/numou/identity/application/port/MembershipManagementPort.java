package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

public interface MembershipManagementPort {
    Uni<Boolean> isActive(TenantId tenantId, UserId userId);
}
