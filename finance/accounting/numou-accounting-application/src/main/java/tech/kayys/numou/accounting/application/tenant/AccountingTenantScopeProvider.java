package tech.kayys.numou.accounting.application.tenant;

import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

/**
 * Resolves the authenticated tenant for an Accounting application operation.
 *
 * <p>Persistence adapters must use this scope for every tenant-owned query and
 * write. Implementations must fail closed when no authenticated tenant has
 * been bound; the domain's default tenant is not an authorization fallback.</p>
 */
@FunctionalInterface
public interface AccountingTenantScopeProvider {
    TenantRef currentTenant();
}
