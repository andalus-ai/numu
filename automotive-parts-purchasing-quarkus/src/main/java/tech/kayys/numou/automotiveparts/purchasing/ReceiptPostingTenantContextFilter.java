package tech.kayys.numou.automotiveparts.purchasing;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;

import java.util.UUID;

/**
 * Binds only a tenant-bound access token issued by Identity after active membership validation.
 * Caller-controlled tenant headers, query parameters and payload fields are never consulted.
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 100)
public class ReceiptPostingTenantContextFilter implements ContainerRequestFilter {
    private static final String BASE_PATH = "api/v1/automotive-parts/receipt-postings";

    private final SecurityIdentity identity;
    private final RequestContextHolder requestContext;

    @Inject
    public ReceiptPostingTenantContextFilter(SecurityIdentity identity, RequestContextHolder requestContext) {
        this.identity = identity;
        this.requestContext = requestContext;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        while (path != null && path.startsWith("/")) path = path.substring(1);
        if (path == null || !(path.equals(BASE_PATH) || path.startsWith(BASE_PATH + "/"))) return;
        if (identity.isAnonymous()) throw new WebApplicationException("Authentication required", 401);

        try {
            String tenantId = resolveVerifiedTenant(
                    identity.getAttribute("principalType"),
                    identity.getAttribute("token_use"),
                    identity.getAttribute("tenant_id"),
                    identity.getAttribute("numou_tenant_id"));
            requestContext.verifiedTenant(tenantId);
        } catch (IllegalArgumentException invalidCredential) {
            throw new WebApplicationException("Tenant-bound access credential required", 403);
        }
    }

    static String resolveVerifiedTenant(String principalType, String tokenUse,
                                         String canonicalTenant, String compatibilityTenant) {
        if (!"USER".equals(principalType) || !"access".equals(tokenUse)) {
            throw new IllegalArgumentException("A human access token is required");
        }
        if (canonicalTenant == null || canonicalTenant.isBlank()) canonicalTenant = compatibilityTenant;
        else if (compatibilityTenant != null && !compatibilityTenant.isBlank()) {
            UUID canonical = canonicalUuid(canonicalTenant);
            UUID compatibility = canonicalUuid(compatibilityTenant);
            if (!canonical.equals(compatibility)) throw new IllegalArgumentException("Tenant claims disagree");
            return canonical.toString();
        }
        return canonicalUuid(canonicalTenant).toString();
    }

    private static UUID canonicalUuid(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Tenant claim is required");
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) throw new IllegalArgumentException("Tenant claim must be canonical");
        return parsed;
    }
}
