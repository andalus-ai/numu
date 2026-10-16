package tech.kayys.numou.foundation.domain.repository;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * Marks an aggregate or domain object as owned by a specific tenant (security02.md §P3-19.1).
 */
public interface TenantAware {

    /**
     * The owning tenant.
     *
     * @return non-null TenantId
     */
    TenantId tenantId();
}
