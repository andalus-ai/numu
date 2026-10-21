package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.kayys.numou.mobility.driver.application.DriverService;
import tech.kayys.numou.mobility.driver.application.DriverView;
import tech.kayys.numou.mobility.ride.application.RideService;
import tech.kayys.numou.mobility.ride.application.RideView;
import tech.kayys.numou.security.adapter.Secured;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.domain.valueobject.ActionType;

import java.util.UUID;

import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.DRIVER_APPROVAL;
import static tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.RIDE_OPS;

/** Operations: driver verification and ride intervention. Every action is audited with who did it. NOT COMPILED in the authoring sandbox. */
@Path("/api/v1/ops")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpsResource {

    public record ReasonRequest(@Size(max = 300) String reason) { }

    public record RefundRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("1") java.math.BigDecimal amount,
                                @jakarta.validation.constraints.DecimalMin("0") java.math.BigDecimal driverShare,
                                @jakarta.validation.constraints.NotBlank @Size(max = 80) String caseRef) { }

    public record PayoutResultRequest(boolean ok, @Size(max = 200) String reason) { }

    @Inject DriverService drivers;
    @Inject RideService rides;
    @Inject tech.kayys.numou.mobility.ride.application.SupportService support;
    @Inject tech.kayys.numou.mobility.wallet.application.PayoutService payouts;
    @Inject Secured secured;
    @Inject AuthorizationService authz;

    @POST
    @Path("/drivers/{id}/approve")
    public Uni<DriverView> approve(@PathParam("id") UUID id) {
        return secured.run("ops.driver.approve", ActionType.PERFORM, DRIVER_APPROVAL, ctx -> drivers.approve(ctx.tenantId(), id)
                .flatMap(v -> authz.auditPerformed(ctx, "DRIVER_APPROVED", "Driver", id.toString(), "").replaceWith(v)));
    }

    @POST
    @Path("/drivers/{id}/reject")
    public Uni<DriverView> reject(@PathParam("id") UUID id, @Valid ReasonRequest r) {
        return secured.run("ops.driver.reject", ActionType.PERFORM, DRIVER_APPROVAL, ctx -> drivers.reject(ctx.tenantId(), id, r == null ? null : r.reason())
                .flatMap(v -> authz.auditPerformed(ctx, "DRIVER_REJECTED", "Driver", id.toString(), "").replaceWith(v)));
    }

    @GET
    @Path("/rides/{id}")
    public Uni<RideView> ride(@PathParam("id") UUID id) {
        return secured.run("ops.ride.get", ActionType.READ, RIDE_OPS, ctx -> rides.get(ctx.tenantId(), Actors.ops(ctx), id));
    }

    @POST
    @Path("/rides/{id}/cancel")
    public Uni<RideView> cancel(@PathParam("id") UUID id, @Valid ReasonRequest r) {
        return secured.run("ops.ride.cancel", ActionType.PERFORM, RIDE_OPS, ctx -> rides.cancelBySystem(ctx.tenantId(), id, r == null ? "ops" : r.reason())
                .flatMap(v -> authz.auditPerformed(ctx, "RIDE_CANCELLED_BY_OPS", "Ride", id.toString(), "").replaceWith(v)));
    }

    @GET
    @Path("/rides/{id}/trace")
    public Uni<tech.kayys.numou.mobility.ride.application.TraceView> trace(@PathParam("id") UUID id) {
        return secured.run("ops.ride.trace", ActionType.READ, RIDE_OPS, ctx -> rides.trace(ctx.tenantId(), Actors.ops(ctx), id));
    }

    /** Goodwill refund / fare correction. Idempotent on {@code caseRef}; capped at what the rider paid; optional part charged to the driver. */
    @POST
    @Path("/rides/{id}/refund")
    public Uni<tech.kayys.numou.mobility.ride.application.SupportService.RefundResult> refund(@PathParam("id") UUID id, @Valid RefundRequest r) {
        return secured.run("ops.ride.refund", ActionType.PERFORM, RIDE_OPS, ctx -> support.refund(ctx.tenantId(), id, r.amount(), r.driverShare(), r.caseRef())
                .flatMap(res -> authz.auditPerformed(ctx, "RIDE_REFUNDED", "Ride", id.toString(), r.caseRef()).replaceWith(res)));
    }

    /** What finance has to transfer. Includes bank account references: PayoutOps permission only. */
    @GET
    @Path("/payouts")
    public Uni<java.util.List<tech.kayys.numou.mobility.wallet.application.PayoutService.PayoutOpsView>> payouts(
            @QueryParam("status") @DefaultValue("SUBMITTED") tech.kayys.numou.mobility.wallet.domain.Payout.Status status, @QueryParam("limit") @DefaultValue("200") int limit) {
        return secured.run("ops.payout.list", ActionType.READ, tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.PAYOUT_OPS,
                ctx -> payouts.listByStatus(ctx.tenantId(), status, limit));
    }

    /** Records the bank result. Failed = the money returns to the driver's wallet, exactly once. */
    @POST
    @Path("/payouts/{id}/result")
    public Uni<Void> payoutResult(@PathParam("id") UUID id, @Valid PayoutResultRequest r) {
        return secured.run("ops.payout.result", ActionType.PERFORM, tech.kayys.numou.security.application.authz.RolePermissionMatrix.Resources.PAYOUT_OPS,
                ctx -> payouts.onResult(new tech.kayys.numou.mobility.wallet.spi.PayoutProvider.Result(ctx.tenantId(), id, r.ok(), r.reason() == null ? "" : r.reason()))
                        .flatMap(x -> authz.auditPerformed(ctx, r.ok() ? "PAYOUT_PAID" : "PAYOUT_FAILED", "Payout", id.toString(), "").replaceWithVoid()));
    }
}
