package tech.kayys.numou.automotiveparts.purchasing;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;

import java.util.Set;
import java.util.UUID;

/**
 * Binds only a cryptographically verified, audience-bound tenant access token to the
 * receipt-posting request context. Caller-controlled tenant headers, query parameters
 * and payload fields are deliberately ignored.
 */
@Provider
@Priority(Priorities.AUTHORIZATION - 100)
public class ReceiptPostingTenantContextFilter implements ContainerRequestFilter {
    private static final String BASE_PATH = "api/v1/automotive-parts/receipt-postings";

    private final SecurityIdentity identity;
    private final RequestContextHolder requestContext;
    private final JsonWebToken token;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String expectedIssuer;

    @ConfigProperty(name = "numou.oauth.access-token.audience")
    String expectedAudience;

    @Inject
    public ReceiptPostingTenantContextFilter(SecurityIdentity identity,
                                             RequestContextHolder requestContext,
                                             JsonWebToken token) {
        this.identity = identity;
        this.requestContext = requestContext;
        this.token = token;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        while (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path == null || !(path.equals(BASE_PATH) || path.startsWith(BASE_PATH + "/"))) {
            return;
        }
        if (identity.isAnonymous()) {
            throw new WebApplicationException("Authentication required", 401);
        }

        try {
            requireIssuerAndAudience(token.getIssuer(), token.getAudience(), expectedIssuer, expectedAudience);
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

    static void requireIssuerAndAudience(String actualIssuer, Set<String> actualAudiences,
                                         String expectedIssuer, String expectedAudience) {
        if (expectedIssuer == null || expectedIssuer.isBlank()
                || actualIssuer == null || !expectedIssuer.equals(actualIssuer)) {
            throw new IllegalArgumentException("Access token issuer is not trusted");
        }
        if (expectedAudience == null || expectedAudience.isBlank()
                || actualAudiences == null || !actualAudiences.contains(expectedAudience)) {
            throw new IllegalArgumentException("Access token audience is not valid for this API");
        }
    }

    static String resolveVerifiedTenant(String principalType, String tokenUse,
                                        String canonicalTenant, String compatibilityTenant) {
        if (!"USER".equals(principalType) || !"access".equals(tokenUse)) {
            throw new IllegalArgumentException("A human access token is required");
        }
        if (canonicalTenant == null || canonicalTenant.isBlank()) {
            canonicalTenant = compatibilityTenant;
        } else if (compatibilityTenant != null && !compatibilityTenant.isBlank()) {
            UUID canonical = canonicalUuid(canonicalTenant);
            UUID compatibility = canonicalUuid(compatibilityTenant);
            if (!canonical.equals(compatibility)) {
                throw new IllegalArgumentException("Tenant claims disagree");
            }
            return canonical.toString();
        }
        return canonicalUuid(canonicalTenant).toString();
    }

    private static UUID canonicalUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tenant claim is required");
        }
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("Tenant claim must be a canonical UUID");
        }
        return parsed;
    }
}
