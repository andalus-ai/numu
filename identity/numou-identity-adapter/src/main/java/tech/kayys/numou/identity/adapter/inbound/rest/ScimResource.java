package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.adapter.inbound.security.IdentityManagementGuard;
import tech.kayys.numou.identity.application.dto.ScimGroup;
import tech.kayys.numou.identity.application.dto.ScimGroupMember;
import tech.kayys.numou.identity.application.dto.ScimPatchRequest;
import tech.kayys.numou.identity.application.dto.ScimUser;
import tech.kayys.numou.identity.application.dto.ScimUserWrite;
import tech.kayys.numou.identity.application.service.ScimProvisioningService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/api/v1/scim/{providerId}/v2")
@Tag(name = "SCIM Provisioning")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScimResource {

    @Inject
    ScimProvisioningService scimProvisioningService;

    @Inject
    IdentityManagementGuard guard;

    @POST
    @Path("/Users")
    @Operation(summary = "Create or adopt a SCIM user (idempotent on externalId)")
    public Uni<Response> createUser(
            @PathParam("providerId") UUID providerId,
            @Valid ScimUserWrite write
    ) {
        return guard.require("identity.user.manage")
                .chain(actor -> scimProvisioningService.createOrAdoptUser(providerId, write))
                .map(user -> Response.status(Response.Status.CREATED).entity(user).build());
    }

    @GET
    @Path("/Users/{userId}")
    @Operation(summary = "Get a SCIM user by internal id")
    public Uni<Response> getUser(
            @PathParam("providerId") UUID providerId,
            @PathParam("userId") UUID userId
    ) {
        return guard.require("identity.user.manage")
                .chain(actor -> scimProvisioningService.getUser(providerId, userId))
                .map(user -> Response.ok(user).build());
    }

    @PUT
    @Path("/Users/{userId}")
    @Operation(summary = "Replace a SCIM user (PUT semantics)")
    public Uni<Response> replaceUser(
            @PathParam("providerId") UUID providerId,
            @PathParam("userId") UUID userId,
            @Valid ScimUserWrite write
    ) {
        return guard.require("identity.user.manage")
                .chain(actor -> scimProvisioningService.replaceUser(providerId, userId, write))
                .map(user -> Response.ok(user).build());
    }

    @PATCH
    @Path("/Users/{userId}")
    @Operation(summary = "Apply a SCIM PATCH operation list to a user")
    public Uni<Response> patchUser(
            @PathParam("providerId") UUID providerId,
            @PathParam("userId") UUID userId,
            @Valid ScimPatchRequest patch
    ) {
        return guard.require("identity.user.manage")
                .chain(actor -> scimProvisioningService.patchUser(
                        providerId, userId, patch.operations()))
                .map(user -> Response.ok(user).build());
    }

    @DELETE
    @Path("/Users/{userId}")
    @Operation(summary = "Deprovision a SCIM user: deactivates and revokes all scoped role assignments")
    public Uni<Response> deprovisionUser(
            @PathParam("providerId") UUID providerId,
            @PathParam("userId") UUID userId
    ) {
        return guard.require("identity.user.manage")
                .chain(actor -> scimProvisioningService.deprovisionUser(providerId, userId))
                .replaceWith(Response.noContent().build());
    }

    // ------------------------------------------------------------------ groups

    @GET
    @Path("/Groups")
    @Operation(summary = "List the provider's virtual SCIM groups (one per groupRoleMappings entry)")
    public Uni<Response> listGroups(@PathParam("providerId") UUID providerId) {
        return guard.require("identity.role.manage")
                .chain(actor -> scimProvisioningService.listGroups(providerId))
                .map(groups -> Response.ok(groups).build());
    }

    @GET
    @Path("/Groups/{groupName}")
    @Operation(summary = "Get one virtual SCIM group with its current members")
    public Uni<Response> getGroup(
            @PathParam("providerId") UUID providerId,
            @PathParam("groupName") String groupName
    ) {
        return guard.require("identity.role.manage")
                .chain(actor -> scimProvisioningService.listGroups(providerId))
                .map(groups -> groups.stream()
                        .filter(g -> g.displayName().equals(groupName) || g.id().equals(groupName))
                        .findFirst()
                        .map(group -> Response.ok(group).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build()));
    }

    @PUT
    @Path("/Groups/{groupName}")
    @Operation(summary = "Replace a virtual SCIM group's membership (grants/revokes the mapped role)")
    public Uni<Response> replaceGroupMembers(
            @PathParam("providerId") UUID providerId,
            @PathParam("groupName") String groupName,
            List<@Valid ScimGroupMember> members
    ) {
        var memberUserIds = members.stream()
                .map(ScimGroupMember::value)
                .collect(Collectors.toList());
        return guard.require("identity.role.manage")
                .chain(actor -> scimProvisioningService.replaceGroupMembers(
                        providerId, groupName, memberUserIds))
                .map(group -> Response.ok(group).build());
    }
}