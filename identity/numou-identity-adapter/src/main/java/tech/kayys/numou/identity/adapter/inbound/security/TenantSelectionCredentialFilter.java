package tech.kayys.numou.identity.adapter.inbound.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Restricts tenant-selection credentials to tenant discovery and exchange.
 * They must never be usable as bearer credentials on ordinary business APIs.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class TenantSelectionCredentialFilter implements ContainerRequestFilter {
    private final SecurityIdentity identity;

    @Inject
    public TenantSelectionCredentialFilter(SecurityIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (identity.isAnonymous()
                || !"tenant_selection".equals(identity.getAttribute("token_use"))) {
            return;
        }
        if (!isAllowedSelectionRequest(request.getMethod(), request.getUriInfo().getPath())) {
            throw new WebApplicationException("Tenant-selection credential cannot access this resource", 403);
        }
    }

    static boolean isAllowedSelectionRequest(String method, String rawPath) {
        if (method == null || rawPath == null) {
            return false;
        }
        String path = rawPath;
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return ("GET".equalsIgnoreCase(method) && "api/v2/auth/tenants".equals(path))
                || ("POST".equalsIgnoreCase(method) && "api/v2/auth/tenant-exchange".equals(path));
    }
}
