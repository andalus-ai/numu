package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.application.dto.AccessTokenResponse;
import tech.kayys.numou.identity.application.dto.LoginRequest;
import tech.kayys.numou.identity.application.dto.TenantExchangeRequest;
import tech.kayys.numou.identity.application.service.TenantSelectionLoginService;
import tech.kayys.numou.identity.application.service.TenantSelectionService;

import java.util.List;
import java.util.UUID;

/** Versioned human authentication flow: login -> discover tenants -> exchange for tenant access. */
@Path("/api/v2/auth")
@Tag(name = "Tenant authentication")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TenantAuthResource {
    private final JsonWebToken jwt;
    private final TenantSelectionLoginService login;
    private final TenantSelectionService tenants;

    @Inject
    public TenantAuthResource(JsonWebToken jwt, TenantSelectionLoginService login,
                              TenantSelectionService tenants) {
        this.jwt = jwt;
        this.login = login;
        this.tenants = tenants;
    }

    @POST
    @Path("/login")
    @Operation(summary = "Authenticate a local user and issue a short-lived tenant-selection credential")
    public Uni<AccessTokenResponse> login(LoginRequest request) {
        return login.login(request);
    }

    @GET
    @Path("/tenants")
    @Operation(summary = "List active tenant memberships for the authenticated user")
    public Uni<List<UUID>> eligibleTenants() {
        SelectionPrincipal principal = requireSelectionPrincipal();
        return tenants.eligibleTenants(principal.id());
    }

    @POST
    @Path("/tenant-exchange")
    @Operation(summary = "Revalidate tenant membership and issue a tenant-bound access token")
    public Uni<AccessTokenResponse> exchange(TenantExchangeRequest request) {
        SelectionPrincipal principal = requireSelectionPrincipal();
        return tenants.exchange(principal.id(), principal.displayName(),
                request == null ? null : request.tenantId());
    }

    private SelectionPrincipal requireSelectionPrincipal() {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new WebApplicationException("Authentication required", 401);
        }
        if (!"tenant_selection".equals(jwt.getClaim("token_use"))
                || !"USER".equals(jwt.getClaim("principalType"))) {
            throw new WebApplicationException("Tenant-selection credential required", 403);
        }
        try {
            UUID userId = UUID.fromString(jwt.getSubject());
            String upn = jwt.getClaim("upn");
            String displayName = upn != null && !upn.isBlank() ? upn : jwt.getSubject();
            return new SelectionPrincipal(userId, displayName);
        } catch (RuntimeException invalidSubject) {
            throw new WebApplicationException("Authenticated user identity is invalid", 401);
        }
    }

    private record SelectionPrincipal(UUID id, String displayName) { }
}
