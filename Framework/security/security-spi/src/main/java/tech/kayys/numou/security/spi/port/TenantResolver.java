package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

/**
 * Resolves and validates a tenant for an authenticated principal.
 */
public interface TenantResolver {

    /**
     * Resolves the authoritative TenantId. Fails if the requested tenant is invalid
     * or the principal is not a member of the requested tenant.
     *
     * @param request the resolution request containing principal and requested tenant selector
     * @return reactive Uni emitting the validated TenantId
     */
    Uni<TenantId> resolve(TenantResolutionRequest request);
}
