package tech.kayys.syirkah.security.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authorization.Permission;
import tech.kayys.syirkah.security.domain.authorization.Principal;

import java.util.Set;

/**
 * Loads the grants effective for a principal within a tenant scope (base01.md §P1-17, security01.md §3.4).
 *
 * <p>The evaluator stays a pure domain service; where grants come from
 * (role store, IAM provider, feature flags) is infrastructure.
 */
public interface PolicyPort {

    /**
     * Loads effective permissions for the given principal within the specified tenant scope.
     *
     * @param principal the asking principal
     * @param tenantId the tenant scope
     * @return reactive Uni producing the set of granted permissions
     */
    Uni<Set<Permission>> permissionsFor(Principal principal, TenantId tenantId);

    /**
     * Loads effective permissions for the principal across default/unscoped tenancy.
     *
     * @param principal the asking principal
     * @return reactive Uni producing the set of granted permissions
     */
    default Uni<Set<Permission>> permissionsFor(Principal principal) {
        return permissionsFor(principal, null);
    }
}
