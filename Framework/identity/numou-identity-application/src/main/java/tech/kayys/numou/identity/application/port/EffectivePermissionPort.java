package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.util.Set;

/**
 * Port for loading effective permissions for a user within a tenant scope (security02.md §P3-16).
 */
public interface EffectivePermissionPort {

    /**
     * Resolves all distinct permission codes granted to the user in the specified tenant.
     *
     * @param tenantId the tenant scope
     * @param userId   the user ID
     * @return reactive Uni emitting set of permission codes (e.g. "product.read")
     */
    Uni<Set<String>> permissionsFor(
            TenantId tenantId,
            UserId userId);
}
