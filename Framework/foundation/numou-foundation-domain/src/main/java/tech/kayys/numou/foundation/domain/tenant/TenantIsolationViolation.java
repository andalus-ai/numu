package tech.kayys.numou.foundation.domain.tenant;

/**
 * Thrown when an aggregate operation violates tenant isolation boundaries (security02.md §P3-19.5).
 */
public final class TenantIsolationViolation extends RuntimeException {

    private final TenantId currentTenant;
    private final TenantId aggregateTenant;

    public TenantIsolationViolation(TenantId currentTenant, TenantId aggregateTenant) {
        super("Tenant isolation violation: current tenant ["
                + (currentTenant != null ? currentTenant.value() : "null")
                + "] cannot access or persist aggregate belonging to tenant ["
                + (aggregateTenant != null ? aggregateTenant.value() : "null") + "]");
        this.currentTenant = currentTenant;
        this.aggregateTenant = aggregateTenant;
    }

    public TenantId currentTenant() {
        return currentTenant;
    }

    public TenantId aggregateTenant() {
        return aggregateTenant;
    }
}
