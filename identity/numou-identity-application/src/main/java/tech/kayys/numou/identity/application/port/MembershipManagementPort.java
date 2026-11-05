package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;

public interface MembershipManagementPort {
    Uni<Boolean> isActive(TenantId tenantId, UserId userId);

    /**
     * Returns active tenant memberships for an active user, using the same
     * authoritative membership records as isActive.
     */
    default Uni<List<TenantId>> activeTenantIds(UserId userId) {
        return Uni.createFrom().failure(
                new UnsupportedOperationException("Active tenant discovery is not supported by this adapter"));
    }
}
