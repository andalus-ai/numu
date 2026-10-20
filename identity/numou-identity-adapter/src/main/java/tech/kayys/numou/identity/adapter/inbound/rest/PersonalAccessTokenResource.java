package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.adapter.inbound.security.IdentityManagementGuard;
import tech.kayys.numou.identity.application.dto.CreatePatRequest;
import tech.kayys.numou.identity.application.dto.PatResponse;
import tech.kayys.numou.identity.application.service.PersonalAccessTokenService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/personal-access-tokens")
@Tag(name = "Personal Access Tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PersonalAccessTokenResource {

    private final PersonalAccessTokenService patService;
    private final IdentityManagementGuard guard;

    @Inject
    public PersonalAccessTokenResource(
            PersonalAccessTokenService patService,
            IdentityManagementGuard guard
    ) {
        this.patService = patService;
        this.guard = guard;
    }

    @POST
    @Operation(summary = "Issue a new PAT. The raw token is returned exactly once in this response.")
    public Uni<Response> issue(@Valid CreatePatRequest request) {
        return guard.require("identity.pat.manage").chain(actor -> {
            if (!"USER".equalsIgnoreCase(request.principalType()) || !actor.equals(request.principalId())) {
                throw new WebApplicationException("Cannot issue PAT for another principal", 403);
            }
            return patService.issue(request);
        }).map(pat -> Response.status(Response.Status.CREATED).entity(pat).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Revoke an owned PAT")
    public Uni<Response> revoke(@PathParam("id") UUID id) {
        return guard.require("identity.pat.manage")
                .chain(actor -> patService.revokeOwned(id, actor))
                .map(v -> Response.noContent().build());
    }

    @GET
    @Path("/principal/{principalType}/{principalId}")
    @Operation(summary = "List a principal's tokens (metadata only — never the raw secret or hash)")
    public Uni<List<PatResponse>> listForPrincipal(
            @PathParam("principalType") String principalType,
            @PathParam("principalId") UUID principalId
    ) {
        return guard.require("identity.pat.manage").chain(actor -> {
            if (!"USER".equalsIgnoreCase(principalType) || !actor.equals(principalId)) {
                throw new WebApplicationException("Forbidden", 403);
            }
            return patService.listForPrincipal(principalType.toUpperCase(java.util.Locale.ROOT), principalId);
        });
    }
}
