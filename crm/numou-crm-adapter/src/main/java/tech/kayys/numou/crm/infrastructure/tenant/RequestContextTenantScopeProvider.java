package tech.kayys.numou.crm.infrastructure.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.crm.application.tenant.TenantScope;
import tech.kayys.numou.crm.application.tenant.TenantScopeProvider;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;

/**
 * Resolves CRM tenant scope only from the shared request context after the
 * security layer has explicitly marked the tenant as verified.
 *
 * <p>This adapter deliberately does not read headers, command fields, or the
 * legacy unverified tenant context. Non-HTTP callers should provide a separate
 * adapter backed by a validated job/message tenant envelope.</p>
 */
@ApplicationScoped
public class RequestContextTenantScopeProvider implements TenantScopeProvider {

    private final RequestContextHolder requestContext;

    @Inject
    public RequestContextTenantScopeProvider(RequestContextHolder requestContext) {
        this.requestContext = requestContext;
    }

    @Override
    public TenantScope requireTenantScope() {
        if (!requestContext.tenantVerified()) {
            throw new SecurityException("CRM operation requires a verified tenant context");
        }

        String rawTenantId = requestContext.tenantId();
        if (rawTenantId == null || rawTenantId.isBlank()) {
            throw new SecurityException("Verified CRM tenant context has no tenant ID");
        }

        final UUID tenantUuid;
        try {
            tenantUuid = UUID.fromString(rawTenantId);
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("Verified CRM tenant ID is malformed", exception);
        }

        return new TenantScope(TenantId.of(tenantUuid));
    }
}
