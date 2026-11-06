package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.UUID;

/** Read-only projection of active tenant memberships for tenant selection. */
public interface TenantDiscoveryPort {
    Uni<List<UUID>> activeTenantIds(UserId userId);
}
