package tech.kayys.numou.foundation.domain.tenant;

import tech.kayys.numou.foundation.domain.repository.TenantAware;

/**
 * Enforces tenant boundary invariants (security02.md §P3-19.5).
 */
public final class TenantIsolation {

    private TenantIsolation() {
    }

    /**
     * Verifies that the aggregate is owned by the current execution tenant.
     *
     * @param currentTenant the tenant in the current execution context
     * @param aggregate     the aggregate to verify
     * @throws IllegalStateException    if currentTenant is null
     * @throws IllegalArgumentException if aggregate is null
     * @throws TenantIsolationViolation if currentTenant != aggregate.tenantId()
     */
    public static void verify(TenantId currentTenant, TenantAware aggregate) {
        if (currentTenant == null) {
            throw new IllegalStateException("Tenant context is required for tenant-scoped operation");
        }
        if (aggregate == null) {
            throw new IllegalArgumentException("aggregate cannot be null");
        }
        if (!currentTenant.equals(aggregate.tenantId())) {
            throw new TenantIsolationViolation(currentTenant, aggregate.tenantId());
        }
    }
}
