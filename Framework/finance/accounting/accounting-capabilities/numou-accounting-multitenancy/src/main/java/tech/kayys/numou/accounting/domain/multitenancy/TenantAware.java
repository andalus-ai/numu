package tech.kayys.syirkah.accounting.domain.multitenancy;

/**
 * Contract implemented by all aggregates requiring multi-tenant partitioning.
 */
public interface TenantAware {
    TenantRef tenantId();
}
