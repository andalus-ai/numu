package tech.kayys.syirkah.survey.adapter.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Map;

/**
 * Resolves the tenant from the X-Tenant-Id header (and the caller from X-Actor-Id).
 * In production derive both from a verified JWT / API key / sub-domain instead of trusting raw headers.
 */
@Provider
public class TenantRequestFilter implements ContainerRequestFilter {
    @Inject RequestTenant tenant;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("/api/v1/admin") || path.startsWith("/q/")) return; // operator + Quarkus management endpoints
        String id = ctx.getHeaderString("X-Tenant-Id");
        if (id == null || id.isBlank()) {
            ctx.abortWith(Response.status(400).entity(Map.of("code", "MISSING_TENANT", "message", "X-Tenant-Id header is required")).build());
            return;
        }
        tenant.set(RequestTenant.of(id, ctx.getHeaderString("X-Actor-Id")));
    }
}
