package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.adapter.inbound.security.IdentityManagementGuard;
import tech.kayys.numou.identity.application.dto.GrantRoleAssignmentRequest;
import tech.kayys.numou.identity.application.dto.RoleAssignmentResponse;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/api/v1/role-assignments")
@Tag(name = "Role Assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoleAssignmentResource {

    private final ScopedRoleAssignmentService roleAssignmentService;
    private final IdentityManagementGuard guard;

    @Inject
    public RoleAssignmentResource(
            ScopedRoleAssignmentService roleAssignmentService,
            IdentityManagementGuard guard
    ) {
        this.roleAssignmentService = roleAssignmentService;
        this.guard = guard;
    }

    @POST
    @Operation(summary = "Grant a role to a principal at a hierarchical scope")
    public Uni<Response> grant(@Valid GrantRoleAssignmentRequest request, @HeaderParam("X-Actor-Id") UUID actorId) {
        return guard.requireWithinScope("identity.role-assignment.manage", request.scopePath())
                .chain(actor -> roleAssignmentService.grant(request, actorId != null ? actorId : actor))
                .map(assignment -> Response.status(Response.Status.CREATED).entity(assignment).build());
    }

    @DELETE
    @Path("/principal/{principalType}/{principalId}/role/{roleId}")
    @Operation(summary = "Revoke a role from a principal at a scope")
    public Uni<Response> revoke(
            @PathParam("principalType") String principalType,
            @PathParam("principalId") UUID principalId,
            @PathParam("roleId") UUID roleId,
            @QueryParam("scopePath") String scopePath,
            @HeaderParam("X-Actor-Id") UUID actorId
    ) {
        return guard.requireWithinScope("identity.role-assignment.manage", scopePath)
                .chain(actor -> roleAssignmentService.revoke(
                        principalType.toUpperCase(java.util.Locale.ROOT),
                        principalId,
                        roleId,
                        actorId != null ? actorId : actor,
                        scopePath
                ))
                .map(v -> Response.noContent().build());
    }

    @GET
    @Path("/principal/{principalType}/{principalId}")
    @Operation(summary = "List role assignments for a principal within current scope")
    public Uni<List<RoleAssignmentResponse>> listForPrincipal(
            @PathParam("principalType") String principalType,
            @PathParam("principalId") UUID principalId
    ) {
        return guard.require("identity.role-assignment.manage")
                .chain(actor -> roleAssignmentService.listForPrincipal(
                        principalType.toUpperCase(java.util.Locale.ROOT),
                        principalId
                ))
                .map(list -> list.stream()
                        .filter(a -> a.scopePath().equals(guard.scope()) || a.scopePath().startsWith(guard.scope() + "."))
                        .toList());
    }

    @GET
    @Path("/principal/{principalType}/{principalId}/effective-permissions")
    @Operation(summary = "Resolve effective permissions a principal has at a given scope path")
    public Uni<Set<String>> effectivePermissions(
            @PathParam("principalType") String principalType,
            @PathParam("principalId") UUID principalId,
            @QueryParam("scopePath") String scopePath
    ) {
        return guard.requireWithinScope("identity.role-assignment.manage", scopePath)
                .chain(actor -> roleAssignmentService.resolveEffectivePermissions(
                        principalType.toUpperCase(java.util.Locale.ROOT),
                        principalId,
                        scopePath
                ));
    }
}
