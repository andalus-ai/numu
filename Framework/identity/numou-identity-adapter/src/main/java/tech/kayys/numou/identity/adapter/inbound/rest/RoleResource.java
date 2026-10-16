package tech.kayys.syirkah.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.identity.application.service.RoleManagementService;
import tech.kayys.syirkah.identity.domain.role.RoleId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.net.URI;
import java.util.UUID;

@Path("/api/identity/tenants/{tenantId}/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoleResource {
    private final RoleManagementService roleManagement;

    @Inject
    public RoleResource(RoleManagementService roleManagement) {
        this.roleManagement = roleManagement;
    }

    @POST
    public Uni<Response> create(
            @PathParam("tenantId") UUID tenantId,
            CreateRoleRequest request
    ) {
        return roleManagement.createRole(tenantId.toString(), request.code(), request.name())
                .map(roleId -> Response.created(
                        URI.create("/api/identity/tenants/" + tenantId + "/roles/" + roleId.value())
                ).entity(new RoleResponse(roleId.value(), request.code(), request.name())).build());
    }

    @POST
    @Path("/{roleId}/permissions")
    public Uni<Response> grantPermission(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("roleId") UUID roleId,
            GrantPermissionRequest request
    ) {
        return roleManagement.grantPermission(
                        tenantId.toString(),
                        new RoleId(roleId),
                        request.permission()
                )
                .replaceWith(Response.noContent().build());
    }

    @PUT
    @Path("/{roleId}/members/{userId}")
    public Uni<Response> assignRole(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("roleId") UUID roleId,
            @PathParam("userId") UUID userId
    ) {
        return roleManagement.assignRole(
                        tenantId.toString(),
                        UserId.of(userId),
                        new RoleId(roleId)
                )
                .replaceWith(Response.noContent().build());
    }

    @DELETE
    @Path("/{roleId}/members/{userId}")
    public Uni<Response> removeRole(
            @PathParam("tenantId") UUID tenantId,
            @PathParam("roleId") UUID roleId,
            @PathParam("userId") UUID userId
    ) {
        return roleManagement.removeRole(
                        tenantId.toString(),
                        UserId.of(userId),
                        new RoleId(roleId)
                )
                .replaceWith(Response.noContent().build());
    }

    public record CreateRoleRequest(String code, String name) {}
    public record GrantPermissionRequest(String permission) {}
    public record RoleResponse(UUID id, String code, String name) {}
}
