package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authorization.Principal;

/**
 * Resolves authoritative tenant membership from the identity provider/store.
 */
public interface TenantMembershipResolver {

    /**
     * Checks if the principal is an active member of the specified tenant.
     *
     * @param principal the asking principal
     * @param tenantId  the tenant ID
     * @return reactive Uni indicating whether membership is active
     */
    Uni<Boolean> isMember(Principal principal, TenantId tenantId);
}
