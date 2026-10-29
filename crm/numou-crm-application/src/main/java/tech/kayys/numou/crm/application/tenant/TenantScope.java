package tech.kayys.numou.crm.application.tenant;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * Tenant boundary resolved from a trusted authenticated runtime.
 *
 * <p>This value is not a credential and does not authenticate its creator.
 * Implementations of {@link TenantScopeProvider} must derive it from the
 * host's verified security/membership context, never from request payloads,
 * query parameters, or unchecked headers. Do not serialize this value as an
 * instruction to trust an incoming tenant identifier.</p>
 */
public record TenantScope(TenantId tenantId) {
    public TenantScope {
        Objects.requireNonNull(tenantId, "A verified tenant ID is required");
    }
}
