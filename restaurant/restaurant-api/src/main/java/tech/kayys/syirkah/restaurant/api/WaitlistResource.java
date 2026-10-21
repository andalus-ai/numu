package tech.kayys.syirkah.restaurant.api;

import tech.kayys.syirkah.security.adapter.Secured;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.syirkah.restaurant.reservation.application.WaitlistService;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static tech.kayys.syirkah.security.application.authz.RolePermissionMatrix.Resources.WAITLIST;

@Path("/api/v1/venues/{venueId}/waitlist")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WaitlistResource {

    public record JoinRequest(@NotBlank @Size(max = 120) String name, @Size(max = 32) String phone, @Size(max = 160) String email,
                              @Min(1) @Max(100) int partySize, @Min(1) @Max(200) int comparableTables) { }

    @Inject
    WaitlistService service;
    @Inject
    Secured secured;

    @POST
    public Uni<WaitlistService.View> join(@PathParam("venueId") UUID venueId, @Valid JoinRequest r) {
        return secured.run("waitlist.join", ActionType.CREATE, WAITLIST,
                ctx -> service.join(ctx.tenantId(), venueId, r.name(), r.phone(), r.email(), r.partySize(), r.comparableTables()));
    }

    @GET
    public Uni<List<WaitlistService.View>> queue(@PathParam("venueId") UUID venueId) {
        return secured.run("waitlist.queue", ActionType.READ, WAITLIST, ctx -> service.queue(ctx.tenantId(), venueId));
    }

    @POST
    @Path("/notify-next")
    public Uni<Optional<WaitlistService.View>> notifyNext(@PathParam("venueId") UUID venueId, @QueryParam("capacity") @Min(1) @Max(100) int capacity) {
        return secured.run("waitlist.notify", ActionType.UPDATE, WAITLIST, ctx -> service.notifyNext(ctx.tenantId(), venueId, capacity));
    }

    @POST
    @Path("/{id}/seat")
    public Uni<WaitlistService.View> seat(@PathParam("id") UUID id) {
        return secured.run("waitlist.seat", ActionType.UPDATE, WAITLIST, ctx -> service.seat(ctx.tenantId(), id));
    }

    @POST
    @Path("/{id}/leave")
    public Uni<WaitlistService.View> leave(@PathParam("id") UUID id) {
        return secured.run("waitlist.leave", ActionType.UPDATE, WAITLIST, ctx -> service.leave(ctx.tenantId(), id));
    }
}
