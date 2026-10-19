package tech.kayys.numou.foundation.kernel.deployment;

/**
 * Strategy for tenancy isolation in Numou.
 */
public enum TenancyMode {

    /**
     * Standalone single-tenant mode (e.g. offline POS, local coffee shop terminal).
     * All operations default to a single system tenant without requiring HTTP tenant resolution.
     */
    STANDALONE_SINGLE_TENANT,

    /**
     * Multi-tenant shared database with Row-Level Security (RLS) or discriminator column.
     */
    SHARED_DATABASE_MULTI_TENANT,

    /**
     * Multi-tenant with a dedicated database or schema per tenant.
     */
    DATABASE_PER_TENANT;

    public boolean isMultiTenant() {
        return this != STANDALONE_SINGLE_TENANT;
    }
}
