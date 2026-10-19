package tech.kayys.numou.foundation.application.context;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * Accessor for the active execution context (security02.md §P3-19.4).
 */
public interface ExecutionContextProvider {

    /**
     * Obtains the current execution context.
     *
     * @return the active ExecutionContext
     */
    ExecutionContext current();

    /**
     * Resolves the required tenant ID from the active context.
     *
     * @return non-null TenantId
     * @throws IllegalStateException if tenantId is missing
     */
    default TenantId requireTenant() {
        return current().requireTenant();
    }
}
