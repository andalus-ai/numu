package tech.kayys.numou.restaurant.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.restaurant.venue.application.MenuView;
import tech.kayys.numou.restaurant.venue.application.VenueService;
import tech.kayys.numou.restaurant.venue.domain.MenuItem;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.MENU;

/** Menu reads (cached) and administration. Every write invalidates the cached menu after commit. NOT COMPILED in sandbox. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MenuResource {

    public record ModifierDto(@NotBlank @Size(max = 60) String name, @NotNull @DecimalMin("0") BigDecimal priceDelta) { }

    public record ItemRequest(@NotBlank @Size(max = 60) String section, @NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
                              @NotNull @DecimalMin("0") BigDecimal price, @NotBlank String currency, @NotNull UUID stationId,
                              @Min(1) @Max(240) int prepMinutes, Set<@Size(max = 40) String> allergens, @Size(max = 20) List<@Valid ModifierDto> modifiers) { }

    public record PriceRequest(@NotNull @DecimalMin("0") BigDecimal price, @NotBlank String currency) { }

    @Inject
    VenueService service;
    @Inject
    Secured secured;

    @GET
    @Path("/venues/{venueId}/menu")
    public Uni<MenuView> published(@PathParam("venueId") UUID venueId) {
        return secured.run("menu.published", ActionType.READ, MENU, ctx -> service.publishedMenu(ctx.tenantId(), venueId));
    }

    @POST
    @Path("/menus/{menuId}/items")
    public Uni<Map<String, UUID>> addItem(@PathParam("menuId") UUID menuId, @Valid ItemRequest r) {
        return secured.run("menu.add-item", ActionType.CREATE, MENU, ctx -> service.addItem(ctx.tenantId(), menuId, r.section(), r.name(),
                r.description(), Money.of(r.price(), r.currency()), r.stationId(), Duration.ofMinutes(r.prepMinutes()), r.allergens(),
                r.modifiers() == null ? List.<MenuItem.Modifier>of() : r.modifiers().stream().map(m -> new MenuItem.Modifier(m.name(), Money.of(m.priceDelta(), r.currency()))).toList())
                .map(id -> Map.of("id", id)));
    }

    @PUT
    @Path("/menus/{menuId}/items/{itemId}/price")
    public Uni<Response> price(@PathParam("menuId") UUID menuId, @PathParam("itemId") UUID itemId, @Valid PriceRequest r) {
        return secured.run("menu.price", ActionType.UPDATE, MENU,
                ctx -> service.changePrice(ctx.tenantId(), menuId, itemId, Money.of(r.price(), r.currency())).replaceWith(Response.noContent().build()));
    }

    @POST
    @Path("/menus/{menuId}/publish")
    public Uni<Response> publish(@PathParam("menuId") UUID menuId) {
        return secured.run("menu.publish", ActionType.UPDATE, MENU, ctx -> service.publishMenu(ctx.tenantId(), menuId).replaceWith(Response.noContent().build()));
    }
}
