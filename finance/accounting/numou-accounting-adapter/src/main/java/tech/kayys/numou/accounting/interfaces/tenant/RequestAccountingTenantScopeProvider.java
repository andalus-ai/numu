package tech.kayys.numou.accounting.interfaces.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.application.tenant.AccountingTenantScopeProvider;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.foundation.adapter.context.TenantContext;

/**
 * Bridges the authenticated request tenant established by the shared
 * Foundation security/request-context pipeline into Accounting.
 *
 * <p>This adapter deliberately does not call TenantRef.defaultTenant().
 * Background jobs must establish an explicit tenant context before invoking
 * Accounting persistence.</p>
 */
@ApplicationScoped
public class RequestAccountingTenantScopeProvider implements AccountingTenantScopeProvider {

    @Override
    public TenantRef currentTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException(
                    "Accounting operation requires an authenticated tenant context");
        }
        return TenantRef.of(tenantId);
    }
}
