package tech.kayys.numou.restaurant.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.restaurant.kitchen.application.KitchenService;
import tech.kayys.numou.restaurant.kitchen.application.TicketView;
import tech.kayys.numou.restaurant.kitchen.domain.Priority;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.KITCHEN_AVAILABILITY;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.KITCHEN_TICKET;

/** Kitchen display (KDS) and the 86 board. Poll {@code /queue} every few seconds from the screen. NOT COMPILED in sandbox. */
@Path("/api/v1/venues/{venueId}/kitchen")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KitchenResource {

    public record EightySixRequest(@NotNull UUID menuItemId, @Size(max = 200) String reason, Instant until) { }

    public record VoidItemRequest(@Size(max = 300) String reason) { }

    public record PriorityRequest(@NotNull Priority priority) { }

    @Inject
    KitchenService service;
    @Inject
    Secured secured;

    @GET
    @Path("/queue")
    public Uni<List<TicketView>> queue(@PathParam("venueId") UUID venueId, @QueryParam("station") UUID station) {
        return secured.run("kitchen.queue", ActionType.READ, KITCHEN_TICKET, ctx -> service.queue(ctx.tenantId(), venueId, Optional.ofNullable(station)));
    }

    @POST
    @Path("/tickets/{ticketId}/items/{lineId}/start")
    public Uni<TicketView> start(@PathParam("ticketId") UUID t, @PathParam("lineId") UUID l) {
        return secured.run("kitchen.start", ActionType.UPDATE, KITCHEN_TICKET, ctx -> service.start(ctx.tenantId(), t, l));
    }

    @POST
    @Path("/tickets/{ticketId}/items/{lineId}/ready")
    public Uni<TicketView> ready(@PathParam("ticketId") UUID t, @PathParam("lineId") UUID l) {
        return secured.run("kitchen.ready", ActionType.UPDATE, KITCHEN_TICKET, ctx -> service.markReady(ctx.tenantId(), t, l));
    }

    @POST
    @Path("/tickets/{ticketId}/items/{lineId}/void")
    public Uni<TicketView> voidItem(@PathParam("ticketId") UUID t, @PathParam("lineId") UUID l, @Valid VoidItemRequest r) {
        return secured.run("kitchen.void", ActionType.UPDATE, KITCHEN_TICKET, ctx -> service.voidItem(ctx.tenantId(), t, l, r.reason()));
    }

    @POST
    @Path("/tickets/{ticketId}/bump")
    public Uni<TicketView> bump(@PathParam("ticketId") UUID t) {
        return secured.run("kitchen.bump", ActionType.PERFORM, KITCHEN_TICKET, ctx -> service.bump(ctx.tenantId(), t));
    }

    @POST
    @Path("/tickets/{ticketId}/recall")
    public Uni<TicketView> recall(@PathParam("ticketId") UUID t) {
        return secured.run("kitchen.recall", ActionType.PERFORM, KITCHEN_TICKET, ctx -> service.recall(ctx.tenantId(), t));
    }

    @POST
    @Path("/tickets/{ticketId}/priority")
    public Uni<TicketView> priority(@PathParam("ticketId") UUID t, @Valid PriorityRequest r) {
        return secured.run("kitchen.priority", ActionType.UPDATE, KITCHEN_TICKET, ctx -> service.setPriority(ctx.tenantId(), t, r.priority()));
    }

    @GET
    @Path("/86")
    public Uni<List<KitchenService.BoardEntry>> board(@PathParam("venueId") UUID venueId) {
        return secured.run("kitchen.86.list", ActionType.READ, KITCHEN_AVAILABILITY, ctx -> service.unavailable(ctx.tenantId(), venueId));
    }

    @POST
    @Path("/86")
    public Uni<Response> eightySix(@PathParam("venueId") UUID venueId, @Valid EightySixRequest r) {
        return secured.run("kitchen.86.add", ActionType.PERFORM, KITCHEN_AVAILABILITY,
                ctx -> service.eightySix(ctx.tenantId(), venueId, r.menuItemId(), r.reason(), r.until()).replaceWith(Response.noContent().build()));
    }

    @DELETE
    @Path("/86/{menuItemId}")
    public Uni<Response> restore(@PathParam("venueId") UUID venueId, @PathParam("menuItemId") UUID item) {
        return secured.run("kitchen.86.restore", ActionType.PERFORM, KITCHEN_AVAILABILITY,
                ctx -> service.restore(ctx.tenantId(), venueId, item).replaceWith(Response.noContent().build()));
    }
}
