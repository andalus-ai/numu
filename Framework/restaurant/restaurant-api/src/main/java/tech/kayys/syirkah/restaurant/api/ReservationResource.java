package tech.kayys.syirkah.restaurant.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.syirkah.reliability.application.idempotency.IdempotentExecutor;
import tech.kayys.syirkah.restaurant.reservation.application.ReservationService;
import tech.kayys.syirkah.restaurant.reservation.application.ReservationView;
import tech.kayys.syirkah.restaurant.reservation.domain.BookingSource;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static tech.kayys.syirkah.security.application.authz.RolePermissionMatrix.Resources.RESERVATION;
import static tech.kayys.syirkah.security.application.authz.RolePermissionMatrix.Resources.RESERVATION_WAIVER;

/** Reservations. Mutations are idempotent when the caller sends {@code Idempotency-Key}. NOT COMPILED in sandbox. */
@Path("/api/v1/venues/{venueId}/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationResource {

    public record CreateRequest(@NotBlank @Size(max = 120) String guestName, @Size(max = 32) String guestPhone, @Size(max = 160) String guestEmail,
                                @Min(1) @Max(100) int partySize, @NotNull Instant startsAt, BookingSource source,
                                @Size(max = 500) String notes, @Size(max = 10) Set<@Size(max = 40) String> dietary) { }

    public record CancelRequest(@Size(max = 300) String reason, boolean waivePenalty) { }

    public record NoShowRequest(boolean waivePenalty) { }

    public record DepositRequest(@NotBlank @Size(max = 100) String paymentRef) { }

    public record RescheduleRequest(@NotNull Instant startsAt, @Min(1) @Max(100) int partySize) { }

    @Inject
    ReservationService service;
    @Inject
    Secured secured;
    @Inject
    IdempotentExecutor idempotency;

    @POST
    public Uni<Response> create(@PathParam("venueId") UUID venueId, @HeaderParam("Idempotency-Key") String key, @Valid CreateRequest r) {
        return secured.run("reservation.create", ActionType.CREATE, RESERVATION, ctx -> {
            final var cmd = new ReservationService.CreateCommand(ctx.tenantId(), venueId, r.guestName(), r.guestPhone(), r.guestEmail(),
                    r.partySize(), r.startsAt(), r.source() == null ? BookingSource.WEB : r.source(), r.notes(), r.dietary() == null ? Set.of() : r.dietary());
            final Uni<UUID> id = key == null || key.isBlank()
                    ? service.create(cmd).map(ReservationView::id)
                    // scope includes the tenant: the same key from two tenants can never collide
                    : idempotency.execute(key, ctx.tenantId() + ":reservation.create", () -> service.create(cmd).map(ReservationView::id),
                            UUID::toString, UUID::fromString);
            return id.flatMap(i -> service.get(ctx.tenantId(), i))
                    .map(v -> Response.created(URI.create("/api/v1/venues/" + venueId + "/reservations/" + v.id())).entity(v).build());
        });
    }

    @GET
    @Path("/{id}")
    public Uni<ReservationView> get(@PathParam("id") UUID id) {
        return secured.run("reservation.get", ActionType.READ, RESERVATION, ctx -> service.get(ctx.tenantId(), id));
    }

    @GET
    public Uni<List<ReservationView>> day(@PathParam("venueId") UUID venueId, @QueryParam("date") LocalDate date) {
        return secured.run("reservation.list", ActionType.READ, RESERVATION, ctx -> service.listForDay(ctx.tenantId(), venueId, date));
    }

    @GET
    @Path("/availability")
    public Uni<List<Instant>> availability(@PathParam("venueId") UUID venueId, @QueryParam("date") LocalDate date,
                                           @QueryParam("partySize") @Min(1) @Max(100) int partySize) {
        return secured.run("reservation.availability", ActionType.READ, RESERVATION, ctx -> service.availableSlots(ctx.tenantId(), venueId, date, partySize));
    }

    @POST
    @Path("/{id}/deposit")
    public Uni<ReservationView> deposit(@PathParam("id") UUID id, @Valid DepositRequest r) {
        return secured.run("reservation.deposit", ActionType.UPDATE, RESERVATION, ctx -> service.confirmDeposit(ctx.tenantId(), id, r.paymentRef()));
    }

    @POST
    @Path("/{id}/seat")
    public Uni<ReservationView> seat(@PathParam("id") UUID id) {
        return secured.run("reservation.seat", ActionType.UPDATE, RESERVATION, ctx -> service.seat(ctx.tenantId(), id));
    }

    @POST
    @Path("/{id}/complete")
    public Uni<ReservationView> complete(@PathParam("id") UUID id) {
        return secured.run("reservation.complete", ActionType.UPDATE, RESERVATION, ctx -> service.complete(ctx.tenantId(), id));
    }

    @POST
    @Path("/{id}/reschedule")
    public Uni<ReservationView> reschedule(@PathParam("id") UUID id, @Valid RescheduleRequest r) {
        return secured.run("reservation.reschedule", ActionType.UPDATE, RESERVATION, ctx -> service.reschedule(ctx.tenantId(), id, r.startsAt(), r.partySize()));
    }

    /** Waiving a late fee / forfeited deposit needs the separate ReservationWaiver permission and is audited. */
    @POST
    @Path("/{id}/cancel")
    public Uni<ReservationService.CancelResult> cancel(@PathParam("id") UUID id, @Valid CancelRequest r) {
        return secured.runElevated("reservation.cancel", ActionType.UPDATE, RESERVATION, r.waivePenalty(), RESERVATION_WAIVER,
                "reservation=" + id, ctx -> service.cancel(ctx.tenantId(), id, r.reason(), r.waivePenalty()));
    }

    @POST
    @Path("/{id}/no-show")
    public Uni<ReservationService.CancelResult> noShow(@PathParam("id") UUID id, @Valid NoShowRequest r) {
        return secured.runElevated("reservation.no-show", ActionType.UPDATE, RESERVATION, r.waivePenalty(), RESERVATION_WAIVER,
                "reservation=" + id, ctx -> service.markNoShow(ctx.tenantId(), id, r.waivePenalty()));
    }
}
