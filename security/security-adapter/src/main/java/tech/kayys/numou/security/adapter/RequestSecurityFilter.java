package tech.kayys.numou.security.adapter;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import tech.kayys.numou.foundation.adapter.context.CorrelationContext;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.application.context.TenantGuard;
import tech.kayys.numou.security.application.context.TenantResolution;
import tech.kayys.numou.security.application.problem.Problem;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.identifier.PrincipalId;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Turns an authenticated token into a verified {@link SecurityContext}.
 *
 * <p>This is the fix for header-trusting tenancy: the tenant comes from the validated token; an
 * {@code X-Tenant-Id} header is only accepted if it names a tenant the principal belongs to, and a header that
 * contradicts the token is rejected with 403. Resources that still read the header (the asset resource did) become
 * safe because the request never reaches them with a mismatching value, and
 * {@code RequestContextHolder} refuses later attempts to switch tenants.
 *
 * <p>Authentication itself (401 for missing/invalid tokens) is enforced by Quarkus
 * ({@code quarkus.http.auth.permission.*}); this filter assumes {@link SecurityIdentity} is populated.
 * NOT COMPILED in the authoring sandbox.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class RequestSecurityFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RequestSecurityFilter.class);
    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Inject
    SecurityIdentity identity;
    @Inject
    JsonWebToken token;
    @Inject
    RequestContextHolder holder;
    @Inject
    CurrentSecurity current;
    @Inject
    SecurityConfig config;
    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext ctx) {
        final String path = "/" + ctx.getUriInfo().getPath();
        if (config.security().protectedPrefixes().stream().noneMatch(path::startsWith)) {
            return;
        }
        if (identity.isAnonymous()) {
            ctx.abortWith(ProblemResponses.of(new Problem(401, "unauthenticated", "Unauthenticated", "Authentication is required.", null)));
            return;
        }
        final Principal principal = principal();
        final UUID claimedTenant = uuidClaim(config.security().tenantClaim());
        final var resolution = TenantGuard.resolve(principal, claimedTenant, ctx.getHeaderString(TENANT_HEADER));

        if (resolution instanceof TenantResolution.Rejected rejected) {
            // The reason goes to the log (with correlation id), never to the client.
            LOG.warnf("Tenant resolution rejected principal=%s reason=%s correlationId=%s",
                    principal.id(), rejected.reason(), CorrelationContext.getCorrelationId());
            ctx.abortWith(ProblemResponses.of(new Problem(403, "forbidden", "Forbidden", "You are not allowed to do that.", null)));
            return;
        }
        final UUID tenant = ((TenantResolution.Resolved) resolution).tenantId();
        final var security = new SecurityContext(principal, tenant, token.getGroups(), clientIp());
        current.set(security);
        holder.verifiedTenant(tenant.toString());
        holder.actor(principal.handle());
        // low-cardinality-safe correlation fields for logs (ids only, no personal data)
        MDC.put("tenantId", tenant.toString());
        MDC.put("principalId", principal.id().toString());
    }

    private Principal principal() {
        final String subject = token.getSubject();
        final UUID id = uuidClaim(config.security().userIdClaim()) != null
                ? uuidClaim(config.security().userIdClaim())
                : UUID.nameUUIDFromBytes(("sub:" + subject).getBytes(StandardCharsets.UTF_8)); // stable id for non-UUID subjects
        final Set<UUID> tenants = new HashSet<>();
        final Object memberships = token.getClaim(config.security().tenantsClaim());
        if (memberships instanceof Collection<?> c) {
            c.forEach(o -> parse(String.valueOf(o), tenants));
        }
        final UUID active = uuidClaim(config.security().tenantClaim());
        if (active != null) {
            tenants.add(active);
        }
        final String handle = token.getClaim("preferred_username") != null ? token.getClaim("preferred_username").toString() : subject;
        final Set<tech.kayys.numou.foundation.domain.tenant.TenantId> tenantIds = tenants.stream()
                .map(tech.kayys.numou.foundation.domain.tenant.TenantId::of)
                .collect(java.util.stream.Collectors.toSet());
        return new Principal(PrincipalId.of(id), handle, tenantIds);
    }

    private UUID uuidClaim(String name) {
        final Object v = token.getClaim(name);
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null; // malformed claim == absent claim (then TenantGuard decides)
        }
    }

    private static void parse(String s, Set<UUID> into) {
        try {
            into.add(UUID.fromString(s));
        } catch (IllegalArgumentException ignored) {
            // skip malformed membership entries
        }
    }

    private String clientIp() {
        if (config.security().trustForwardedFor()) {
            final String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.remoteAddress() == null ? "" : request.remoteAddress().host();
    }
}
