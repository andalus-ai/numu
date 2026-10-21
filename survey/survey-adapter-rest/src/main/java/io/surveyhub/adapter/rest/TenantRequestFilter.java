package io.surveyhub.adapter.rest;

import io.surveyhub.security.AuthenticationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;

/**
 * Authenticates every tenant API call and stores the resulting context for the request. Public share-link endpoints,
 * the operator API (admin key) and Quarkus management endpoints are exempt: the former two have their own credentials.
 */
@Provider
public class TenantRequestFilter implements ContainerRequestFilter {
    @Inject RequestTenant tenant;
    @Inject AuthSupport auth;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/")) path = path.substring(1);
        if (path.startsWith("api/v1/admin") || path.startsWith("api/v1/public") || path.startsWith("p/") || path.startsWith("q/")) return;
        try {
            tenant.set(auth.authenticate(ctx));
        } catch (AuthenticationException e) {
            // Details stay in the server log; the client only learns that authentication failed.
            ctx.abortWith(Response.status(401).header("WWW-Authenticate", "Bearer")
                    .entity(Map.of("code", "UNAUTHENTICATED", "message", "Authentication required")).build());
        }
    }
}
