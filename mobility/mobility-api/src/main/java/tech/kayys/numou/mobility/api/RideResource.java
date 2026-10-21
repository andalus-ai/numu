package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.pricing.PaymentMethod;
import tech.kayys.numou.mobility.ride.application.Actor;
import tech.kayys.numou.mobility.ride.application.QuoteView;
import tech.kayys.numou.mobility.ride.application.RideService;
import tech.kayys.numou.mobility.ride.application.RideView;
import tech.kayys.numou.reliability.application.idempotency.IdempotentExecutor;
import tech.kayys.numou.security.adapter.Secured;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.RIDE;

/**
 * Rider API. The rider id is always the token's user id; a rider can only ever see or change their own rides (ownership is also
 * enforced in the application layer, answering "not found" rather than "forbidden"). NOT COMPILED in the authoring sandbox.
 */
@Path("/api/v1/rides")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RideResource {

    public record Point(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat, @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) {
        LatLng toLatLng() { return new LatLng(lat, lng); }
    }

    public record QuoteRequest(@NotNull @Valid Point pickup, @NotNull @Valid Point dropoff, @NotEmpty @Size(max = 3) Set<VehicleType> vehicles,
                               @Size(max = 32) String promoCode,
                               @Size(max = 120) String pickupLabel, @Size(max = 120) String dropoffLabel) { }

    public record BookRequest(@NotBlank @Size(max = 1000) String quoteToken, @NotNull PaymentMethod paymentMethod) { }

    public record CancelRequest(@Size(max = 300) String reason) { }

    public record RatingRequest(@Min(1) @Max(5) int stars) { }

    public record TipRequest(@NotNull @DecimalMin("1") BigDecimal amount) { }

    public record ShareRequest(@Min(1) @Max(6) Integer hours) { }

    public record ChatRequest(@NotBlank @Size(max = 500) String text) { }

    public record DestinationRequest(@NotNull @Valid Point dropoff) { }

    @Inject RideService rides;
    @Inject tech.kayys.numou.mobility.ride.application.ChatService chat;
    @Inject Secured secured;
    @Inject IdempotentExecutor idempotency;

    @POST
    @Path("/quotes")
    public Uni<List<QuoteView>> quotes(@Valid QuoteRequest r) {
        return secured.run("ride.quote", ActionType.CREATE, RIDE, ctx ->
                rides.quote(ctx.tenantId(), Actors.id(ctx), r.pickup().toLatLng(), r.dropoff().toLatLng(), r.pickupLabel(), r.dropoffLabel(), r.vehicles(), r.promoCode()));
    }

    /** {@code Idempotency-Key} is required: mobile networks retry, and a retried tap must not book twice. */
    @POST
    public Uni<RideView> book(@HeaderParam("Idempotency-Key") @NotBlank String key, @Valid BookRequest r) {
        return secured.run("ride.book", ActionType.CREATE, RIDE, ctx -> {
            final UUID rider = Actors.id(ctx);
            return idempotency.execute(key, ctx.tenantId() + ":ride.book",
                            () -> rides.request(ctx.tenantId(), rider, r.quoteToken(), r.paymentMethod()).map(RideView::id), UUID::toString, UUID::fromString)
                    .flatMap(id -> rides.get(ctx.tenantId(), Actor.rider(rider), id));
        });
    }

    @GET
    @Path("/{id}")
    public Uni<RideView> get(@PathParam("id") UUID id) {
        return secured.run("ride.get", ActionType.READ, RIDE, ctx -> rides.get(ctx.tenantId(), Actors.rider(ctx), id));
    }

    @GET
    public Uni<List<RideView>> history(@QueryParam("limit") @DefaultValue("20") @Min(1) @Max(50) int limit) {
        return secured.run("ride.history", ActionType.READ, RIDE, ctx -> rides.history(ctx.tenantId(), Actors.id(ctx), limit));
    }

    /** Server-sent events: the rider's ride (status, driver, live driver position) every 3 s, emitting only when something changed. */
    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<RideView> stream(@PathParam("id") UUID id) {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(3))
                .onItem().<RideView>transformToUniAndConcatenate(tick -> secured.<RideView>run("ride.stream", ActionType.READ, RIDE,
                        ctx -> rides.get(ctx.tenantId(), Actors.rider(ctx), id)))
                .skip().repetitions()
                .select().first(Duration.ofMinutes(45));
    }

    @POST
    @Path("/{id}/cancel")
    public Uni<RideView> cancel(@PathParam("id") UUID id, @Valid CancelRequest r) {
        return secured.run("ride.cancel", ActionType.UPDATE, RIDE, ctx -> rides.cancelByRider(ctx.tenantId(), Actors.id(ctx), id, r == null ? null : r.reason()));
    }

    @POST
    @Path("/{id}/rating")
    public Uni<RideView> rate(@PathParam("id") UUID id, @Valid RatingRequest r) {
        return secured.run("ride.rate", ActionType.UPDATE, RIDE, ctx -> rides.rateAsRider(ctx.tenantId(), Actors.id(ctx), id, r.stars()));
    }

    @POST
    @Path("/{id}/tip")
    public Uni<RideView> tip(@HeaderParam("Idempotency-Key") @NotBlank String key, @PathParam("id") UUID id, @Valid TipRequest r) {
        return secured.run("ride.tip", ActionType.UPDATE, RIDE, ctx -> rides.tip(ctx.tenantId(), Actors.id(ctx), id, r.amount()));
    }

    @POST
    @Path("/{id}/destination")
    public Uni<RideView> destination(@PathParam("id") UUID id, @Valid DestinationRequest r) {
        return secured.run("ride.destination", ActionType.UPDATE, RIDE, ctx -> rides.changeDestination(ctx.tenantId(), Actors.id(ctx), id, r.dropoff().toLatLng()));
    }

    @POST
    @Path("/{id}/sos")
    public Uni<RideView> sos(@PathParam("id") UUID id) {
        return secured.run("ride.sos", ActionType.UPDATE, RIDE, ctx -> rides.sos(ctx.tenantId(), Actors.rider(ctx), id));
    }

    /** A link for family. The token is the only thing to share: build the URL in the app. Valid up to 6 hours, only while the trip is active. */
    @POST
    @Path("/{id}/share")
    public Uni<java.util.Map<String, String>> share(@PathParam("id") UUID id, @Valid ShareRequest r) {
        final Duration d = Duration.ofHours(r == null || r.hours() == null ? 2 : r.hours());
        return secured.run("ride.share", ActionType.UPDATE, RIDE, ctx -> rides.shareLink(ctx.tenantId(), Actors.id(ctx), id, d).map(t -> java.util.Map.of("token", t)));
    }

    /** The route of the trip (simplified, polyline-encoded) for receipts and disputes. */
    @GET
    @Path("/{id}/trace")
    public Uni<tech.kayys.numou.mobility.ride.application.TraceView> trace(@PathParam("id") UUID id) {
        return secured.run("ride.trace", ActionType.READ, RIDE, ctx -> rides.trace(ctx.tenantId(), Actors.rider(ctx), id));
    }

    @GET
    @Path("/{id}/chat")
    public Uni<List<tech.kayys.numou.mobility.ride.application.ChatService.MessageView>> chatHistory(@PathParam("id") UUID id, @QueryParam("after") java.time.Instant after) {
        return secured.run("ride.chat.read", ActionType.READ, RIDE, ctx -> chat.history(ctx.tenantId(), Actors.rider(ctx), id, after));
    }

    @POST
    @Path("/{id}/chat")
    public Uni<tech.kayys.numou.mobility.ride.application.ChatService.MessageView> chatSend(@PathParam("id") UUID id, @Valid ChatRequest r) {
        return secured.run("ride.chat.send", ActionType.UPDATE, RIDE, ctx -> chat.send(ctx.tenantId(), Actors.rider(ctx), id, r.text()));
    }
}
