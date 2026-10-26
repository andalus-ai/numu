package tech.kayys.numou.hospitality.spi;

import io.smallrye.mutiny.Uni;

/**
 * Application authorization boundary. Implementations must evaluate actor membership,
 * provider ownership/role, tenant status, and any required capability permissions.
 */
public interface ProviderAccessPolicy {
    Uni<Void> requireCanManageListings(String tenantId, String actorId, String providerId);
}
