package tech.kayys.numou.accounting.domain.multitenancy;

/**
 * Contract implemented by all aggregates requiring multi-tenant partitioning.
 */
public interface TenantAware {
    TenantRef tenantId();
}
