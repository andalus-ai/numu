package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.driver.application.DriverView;
import tech.kayys.numou.mobility.driver.domain.Vehicle;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.LatLng;
import tech.kayys.numou.mobility.ride.application.DispatchService;
import tech.kayys.numou.mobility.ride.application.RideService;
import tech.kayys.numou.mobility.ride.application.RideView;
import tech.kayys.numou.security.adapter.Secured;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.DRIVER_APP;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.DRIVER_LOCATION;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.RIDE;

/** Driver app API. The driver id is the token's user id. Ride actions are bound to the driver the ride is assigned to. NOT COMPILED in the authoring sandbox. */
@Path("/api/v1/driver")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DriverResource {

    public record VehicleDto(@NotNull VehicleType type, @NotBlank @Size(max = 15) String plate, @Size(max = 60) String model, @Size(max = 30) String color) { }

    public record RegisterRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 32) String phone, @NotNull @Valid VehicleDto vehicle) { }

    public record LocationRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat, @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
                                  @DecimalMin("0") @DecimalMax("360") Double heading, @DecimalMin("0") @DecimalMax("400") Double speedKmh, Instant recordedAt) { }

    public record AtRequest(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat, @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) { }

    public record StartRequest(@NotNull @Pattern(regexp = "\\d{4}") String pin, @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                               @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng) { }

    public record ReasonRequest(@Size(max = 300) String reason) { }

    public record ChatRequest(@NotBlank @Size(max = 500) String text) { }

    public record RatingRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) int stars) { }

    @Inject DriverService drivers;
    @Inject DispatchService dispatch;
    @Inject RideService rides;
    @Inject tech.kayys.numou.mobility.ride.application.ChatService chat;
    @Inject Secured secured;

    @POST
    @Path("/register")
    public Uni<DriverView> register(@Valid RegisterRequest r) {
        return secured.run("driver.register", ActionType.CREATE, DRIVER_APP, ctx -> drivers.register(ctx.tenantId(), Actors.id(ctx), r.name(), r.phone(),
                new Vehicle(r.vehicle().type(), r.vehicle().plate(), r.vehicle().model(), r.vehicle().color())));
    }

    @GET
    @Path("/me")
    public Uni<DriverView> me() {
        return secured.run("driver.me", ActionType.READ, DRIVER_APP, ctx -> drivers.get(ctx.tenantId(), Actors.id(ctx)));
    }

    @POST
    @Path("/online")
    public Uni<DriverView> online() {
        return secured.run("driver.online", ActionType.UPDATE, DRIVER_APP, ctx -> drivers.goOnline(ctx.tenantId(), Actors.id(ctx)));
    }

    @POST
    @Path("/offline")
    public Uni<DriverView> offline() {
        return secured.run("driver.offline", ActionType.UPDATE, DRIVER_APP, ctx -> drivers.goOffline(ctx.tenantId(), Actors.id(ctx)));
    }

    /** One GPS fix. Send every ~4 s while online. Implausible, stale or duplicate fixes are rejected or skipped (see the outcome). */
    @PUT
    @Path("/location")
    public Uni<Map<String, String>> location(@Valid LocationRequest r) {
        return secured.run("driver.location", ActionType.UPDATE, DRIVER_LOCATION, ctx -> rides.trackPosition(ctx.tenantId(), Actors.id(ctx),
                new LatLng(r.lat(), r.lng()), r.heading() == null ? 0 : r.heading(), r.speedKmh() == null ? 0 : r.speedKmh(),
                r.recordedAt() == null ? Instant.now() : r.recordedAt()).map(o -> Map.of("outcome", o.name())));
    }

    /** Poll for an offer (when push is unavailable). 204 when there is none. */
    @GET
    @Path("/offer")
    public Uni<Response> offer() {
        return secured.run("driver.offer", ActionType.READ, RIDE, ctx -> rides.currentOffer(ctx.tenantId(), Actors.id(ctx))
                .map(o -> o.<Response>map(v -> Response.ok(v).build()).orElseGet(() -> Response.noContent().build())));
    }

    @GET
    @Path("/rides/{id}")
    public Uni<RideView> get(@PathParam("id") UUID id) {
        return secured.run("driver.ride.get", ActionType.READ, RIDE, ctx -> rides.get(ctx.tenantId(), Actors.driver(ctx), id));
    }

    @POST
    @Path("/rides/{id}/accept")
    public Uni<RideView> accept(@PathParam("id") UUID id) {
        return secured.run("driver.ride.accept", ActionType.PERFORM, RIDE, ctx -> dispatch.accept(ctx.tenantId(), Actors.id(ctx), id));
    }

    @POST
    @Path("/rides/{id}/decline")
    public Uni<Response> decline(@PathParam("id") UUID id) {
        return secured.run("driver.ride.decline", ActionType.PERFORM, RIDE,
                ctx -> dispatch.decline(ctx.tenantId(), Actors.id(ctx), id).replaceWith(Response.noContent().build()));
    }

    @POST
    @Path("/rides/{id}/arrive")
    public Uni<RideView> arrive(@PathParam("id") UUID id, @Valid AtRequest r) {
        return secured.run("driver.ride.arrive", ActionType.PERFORM, RIDE, ctx -> rides.arrive(ctx.tenantId(), Actors.id(ctx), id, new LatLng(r.lat(), r.lng())));
    }

    @POST
    @Path("/rides/{id}/start")
    public Uni<RideView> start(@PathParam("id") UUID id, @Valid StartRequest r) {
        return secured.run("driver.ride.start", ActionType.PERFORM, RIDE, ctx -> rides.start(ctx.tenantId(), Actors.id(ctx), id, r.pin(), new LatLng(r.lat(), r.lng())));
    }

    @POST
    @Path("/rides/{id}/complete")
    public Uni<RideView> complete(@PathParam("id") UUID id) {
        return secured.run("driver.ride.complete", ActionType.PERFORM, RIDE, ctx -> rides.complete(ctx.tenantId(), Actors.id(ctx), id));
    }

    @POST
    @Path("/rides/{id}/cancel")
    public Uni<RideView> cancel(@PathParam("id") UUID id, @Valid ReasonRequest r) {
        return secured.run("driver.ride.cancel", ActionType.PERFORM, RIDE, ctx -> rides.driverCancel(ctx.tenantId(), Actors.id(ctx), id, r == null ? null : r.reason()));
    }

    @POST
    @Path("/rides/{id}/no-show")
    public Uni<RideView> noShow(@PathParam("id") UUID id) {
        return secured.run("driver.ride.no-show", ActionType.PERFORM, RIDE, ctx -> rides.noShow(ctx.tenantId(), Actors.id(ctx), id));
    }

    @POST
    @Path("/rides/{id}/rating")
    public Uni<RideView> rate(@PathParam("id") UUID id, @Valid RatingRequest r) {
        return secured.run("driver.ride.rate", ActionType.UPDATE, RIDE, ctx -> rides.rateAsDriver(ctx.tenantId(), Actors.id(ctx), id, r.stars()));
    }

    @POST
    @Path("/rides/{id}/sos")
    public Uni<RideView> sos(@PathParam("id") UUID id) {
        return secured.run("driver.ride.sos", ActionType.UPDATE, RIDE, ctx -> rides.sos(ctx.tenantId(), Actors.driver(ctx), id));
    }

    @GET
    @Path("/rides/{id}/chat")
    public Uni<java.util.List<tech.kayys.numou.mobility.ride.application.ChatService.MessageView>> chatHistory(@PathParam("id") UUID id, @QueryParam("after") Instant after) {
        return secured.run("driver.chat.read", ActionType.READ, RIDE, ctx -> chat.history(ctx.tenantId(), Actors.driver(ctx), id, after));
    }

    @POST
    @Path("/rides/{id}/chat")
    public Uni<tech.kayys.numou.mobility.ride.application.ChatService.MessageView> chatSend(@PathParam("id") UUID id, @Valid ChatRequest r) {
        return secured.run("driver.chat.send", ActionType.UPDATE, RIDE, ctx -> chat.send(ctx.tenantId(), Actors.driver(ctx), id, r.text()));
    }

    @GET
    @Path("/rides/{id}/trace")
    public Uni<tech.kayys.numou.mobility.ride.application.TraceView> trace(@PathParam("id") UUID id) {
        return secured.run("driver.ride.trace", ActionType.READ, RIDE, ctx -> rides.trace(ctx.tenantId(), Actors.driver(ctx), id));
    }
}
