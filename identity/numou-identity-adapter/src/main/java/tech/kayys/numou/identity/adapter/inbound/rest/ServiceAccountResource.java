package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.identity.adapter.inbound.security.IdentityManagementGuard;
import tech.kayys.numou.identity.application.dto.CreateServiceAccountRequest;
import tech.kayys.numou.identity.application.dto.ServiceAccountResponse;
import tech.kayys.numou.identity.application.service.ServiceAccountService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/service-accounts")
@Tag(name = "Service Accounts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ServiceAccountResource {

    private final ServiceAccountService serviceAccountService;
    private final IdentityManagementGuard guard;

    @Inject
    public ServiceAccountResource(
            ServiceAccountService serviceAccountService,
            IdentityManagementGuard guard
    ) {
        this.serviceAccountService = serviceAccountService;
        this.guard = guard;
    }

    @POST
    @Operation(summary = "Create a new service account")
    public Uni<Response> create(@Valid CreateServiceAccountRequest request) {
        return guard.require("identity.service-account.manage")
                .chain(actor -> serviceAccountService.create(request))
                .map(sa -> Response.status(Response.Status.CREATED).entity(sa).build());
    }

    @PUT
    @Path("/{id}/disable")
    @Operation(summary = "Disable a service account")
    public Uni<ServiceAccountResponse> disable(@PathParam("id") UUID id) {
        return guard.require("identity.service-account.manage")
                .chain(actor -> serviceAccountService.disable(id, actor));
    }

    @GET
    @Operation(summary = "List all service accounts")
    public Uni<List<ServiceAccountResponse>> listAll() {
        return guard.require("identity.service-account.read")
                .chain(actor -> serviceAccountService.listAll());
    }
}
