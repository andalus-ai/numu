package tech.kayys.syirkah.restaurant.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.syirkah.reliability.application.idempotency.IdempotentExecutor;
import tech.kayys.syirkah.restaurant.order.application.OrderService;
import tech.kayys.syirkah.restaurant.order.application.OrderView;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static tech.kayys.syirkah.security.application.authz.RolePermissionMatrix.Resources.ORDER;
import static tech.kayys.syirkah.security.application.authz.RolePermissionMatrix.Resources.ORDER_VOID;

/**
 * Dine-in orders. The client never sends a price: items are priced from the published menu on the server.
 * Voids and discounts need the OrderVoid permission, and the approving manager is the authenticated caller
 * (never a name taken from the request body). NOT COMPILED in sandbox.
 */
@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    public record OpenRequest(@NotNull UUID venueId, @NotEmpty @Size(max = 5) Set<UUID> tableIds, @Min(1) @Max(100) int covers) { }

    public record AddItemRequest(@NotNull UUID menuItemId, @Min(1) @Max(50) int quantity, @Size(max = 10) List<@Size(max = 60) String> modifiers,
                                 @Size(max = 300) String notes, @Min(1) @Max(9) int course) { }

    public record VoidLineRequest(@Size(max = 300) String reason) { }

    public record DiscountRequest(@NotNull @DecimalMin(value = "0.01") @DecimalMax("100") BigDecimal percent) { }

    public record SettleRequest(@Size(min = 1, max = 100) String paymentRef) { }

    @Inject
    OrderService service;
    @Inject
    Secured secured;
    @Inject
    IdempotentExecutor idempotency;

    @POST
    public Uni<OrderView> open(@HeaderParam("Idempotency-Key") String key, @Valid OpenRequest r) {
        return secured.run("order.open", ActionType.CREATE, ORDER, ctx -> {
            final var open = service.open(ctx.tenantId(), r.venueId(), r.tableIds(), ctx.actor(), r.covers()).map(OrderView::id);
            final Uni<UUID> id = key == null || key.isBlank() ? open
                    : idempotency.execute(key, ctx.tenantId() + ":order.open", () -> open, UUID::toString, UUID::fromString);
            return id.flatMap(i -> service.get(ctx.tenantId(), i));
        });
    }

    @GET
    @Path("/{id}")
    public Uni<OrderView> get(@PathParam("id") UUID id) {
        return secured.run("order.get", ActionType.READ, ORDER, ctx -> service.get(ctx.tenantId(), id));
    }

    @GET
    @Path("/venue/{venueId}/active")
    public Uni<List<OrderView>> active(@PathParam("venueId") UUID venueId) {
        return secured.run("order.active", ActionType.READ, ORDER, ctx -> service.active(ctx.tenantId(), venueId));
    }

    @POST
    @Path("/{id}/items")
    public Uni<OrderView> addItem(@PathParam("id") UUID id, @Valid AddItemRequest r) {
        return secured.run("order.add-item", ActionType.UPDATE, ORDER,
                ctx -> service.addItem(ctx.tenantId(), id, r.menuItemId(), r.quantity(), r.modifiers(), r.notes(), r.course()));
    }

    @DELETE
    @Path("/{id}/items/{lineId}")
    public Uni<OrderView> removePending(@PathParam("id") UUID id, @PathParam("lineId") UUID lineId) {
        return secured.run("order.remove-item", ActionType.UPDATE, ORDER, ctx -> service.removePendingLine(ctx.tenantId(), id, lineId));
    }

    @POST
    @Path("/{id}/courses/{course}/fire")
    public Uni<OrderView> fire(@PathParam("id") UUID id, @PathParam("course") @Min(1) @Max(9) int course) {
        return secured.run("order.fire", ActionType.PERFORM, ORDER, ctx -> service.fire(ctx.tenantId(), id, course));
    }

    @POST
    @Path("/{id}/items/{lineId}/void")
    public Uni<OrderView> voidLine(@PathParam("id") UUID id, @PathParam("lineId") UUID lineId, @Valid VoidLineRequest r) {
        return secured.run("order.void-line", ActionType.PERFORM, ORDER_VOID,
                ctx -> service.voidLine(ctx.tenantId(), id, lineId, r.reason(), ctx.actor())
                        .flatMap(v -> audit(ctx, "ORDER_LINE_VOID", id, "line=" + lineId).replaceWith(v)));
    }

    @POST
    @Path("/{id}/discount")
    public Uni<OrderView> discount(@PathParam("id") UUID id, @Valid DiscountRequest r) {
        return secured.run("order.discount", ActionType.PERFORM, ORDER_VOID,
                ctx -> service.applyDiscount(ctx.tenantId(), id, r.percent(), ctx.actor())
                        .flatMap(v -> audit(ctx, "ORDER_DISCOUNT", id, "percent=" + r.percent()).replaceWith(v)));
    }

    @POST
    @Path("/{id}/bill")
    public Uni<OrderView> bill(@PathParam("id") UUID id) {
        return secured.run("order.bill", ActionType.UPDATE, ORDER, ctx -> service.requestBill(ctx.tenantId(), id));
    }

    @GET
    @Path("/{id}/split")
    public Uni<List<BigDecimal>> split(@PathParam("id") UUID id, @QueryParam("ways") @Min(1) @Max(50) int ways) {
        return secured.run("order.split", ActionType.READ, ORDER, ctx -> service.splitBill(ctx.tenantId(), id, ways));
    }

    @POST
    @Path("/{id}/settle")
    public Uni<OrderView> settle(@PathParam("id") UUID id, @Valid SettleRequest r) {
        return secured.run("order.settle", ActionType.PERFORM, ORDER, ctx -> service.settle(ctx.tenantId(), id, r.paymentRef()));
    }

    @POST
    @Path("/{id}/void")
    public Uni<OrderView> voidOrder(@PathParam("id") UUID id, @Valid VoidLineRequest r) {
        return secured.run("order.void", ActionType.PERFORM, ORDER_VOID,
                ctx -> service.voidOrder(ctx.tenantId(), id, r.reason(), ctx.actor())
                        .flatMap(v -> audit(ctx, "ORDER_VOID", id, "").replaceWith(v)));
    }

    @Inject
    tech.kayys.syirkah.security.application.authz.AuthorizationService authz;

    private Uni<Void> audit(tech.kayys.syirkah.security.application.context.SecurityContext ctx, String action, UUID orderId, String detail) {
        return authz.auditPerformed(ctx, action, ORDER, orderId.toString(), detail);
    }
}
