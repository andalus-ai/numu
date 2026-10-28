package tech.kayys.numou.transport.runtime;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.security.identity.SecurityIdentity;
import tech.kayys.numou.transport.application.DepartureBookingService;
import tech.kayys.numou.transport.domain.PassengerHold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * HTTP adapter for Transport capacity holds. Authentication, active tenant membership,
 * and explicit capability permissions are mandatory. Payment capture and ticket issuance
 * are intentionally separate workflows.
 */
@Authenticated
@Blocking
@ApplicationScoped
@Path("/v1/transport/departures/{departureId}/holds")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransportBookingResource {
    @Inject
    DepartureBookingService bookings;

    @Inject
    SecurityIdentity identity;

    @Inject
    Clock clock;

    @Inject
    TransportBookingAuthorization bookingAuthorization;

    @ConfigProperty(name = "numou.security.tenant-claim", defaultValue = "numou_tenant_id")
    String tenantAttribute;

    @ConfigProperty(name = "numou.security.user-id-claim", defaultValue = "numou_user_id")
    String actorAttribute;

    @ConfigProperty(name = "numou.transport.hold.max-duration-seconds", defaultValue = "900")
    long maxHoldDurationSeconds;

    @POST
    public Response createHold(@PathParam("departureId") UUID departureId,
                               @HeaderParam("Idempotency-Key") String idempotencyKey,
                               CreateHoldRequest request) {
        TransportIdentityContext caller = caller();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new jakarta.ws.rs.BadRequestException("Idempotency-Key must contain 1-128 characters");
        }
        if (request == null || request.fromSequence() == null || request.toSequence() == null
                || request.passengerCount() == null || request.expiresAt() == null) {
            throw new jakarta.ws.rs.BadRequestException("fromSequence, toSequence, passengerCount and expiresAt are required");
        }
        Instant now = clock.instant();
        Instant expiry = request.expiresAt();
        if (maxHoldDurationSeconds < 1 || maxHoldDurationSeconds > 86_400
                || !expiry.isAfter(now)
                || expiry.isAfter(now.plus(Duration.ofSeconds(maxHoldDurationSeconds)))) {
            throw new jakarta.ws.rs.BadRequestException("expiresAt must be in the future and within the configured hold limit");
        }

        bookingAuthorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.CREATE_HOLD, departureId, null);
        PassengerHold hold = bookings.hold(new DepartureBookingService.HoldCommand(
                caller.tenantId(), departureId, idempotencyKey, request.fromSequence(),
                request.toSequence(), request.passengerCount(), expiry));
        return Response.status(Response.Status.CREATED).entity(HoldResponse.from(departureId, hold)).build();
    }

    @POST
    @Path("/{holdId}/confirm")
    public HoldResponse confirm(@PathParam("departureId") UUID departureId,
                                @PathParam("holdId") UUID holdId) {
        TransportIdentityContext caller = caller();
        bookingAuthorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.CONFIRM_HOLD, departureId, holdId);
        return HoldResponse.from(departureId, bookings.confirm(caller.tenantId(), departureId, holdId));
    }

    @POST
    @Path("/{holdId}/release")
    public HoldResponse release(@PathParam("departureId") UUID departureId,
                                @PathParam("holdId") UUID holdId) {
        TransportIdentityContext caller = caller();
        bookingAuthorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.RELEASE_HOLD, departureId, holdId);
        return HoldResponse.from(departureId, bookings.release(caller.tenantId(), departureId, holdId));
    }

    private TransportIdentityContext caller() {
        if (identity == null || identity.isAnonymous()) {
            throw new jakarta.ws.rs.NotAuthorizedException("Authentication is required");
        }
        return TransportIdentityContext.from(identity.getPrincipal().getName(),
                identity.getAttribute(tenantAttribute), identity.getAttribute(actorAttribute));
    }

    public record CreateHoldRequest(Integer fromSequence, Integer toSequence,
                                    Integer passengerCount, Instant expiresAt) {
    }

    public record HoldResponse(UUID holdId, UUID departureId, int fromSequence, int toSequence,
                               int passengerCount, Instant expiresAt, String status,
                               Instant createdAt, Instant updatedAt) {
        static HoldResponse from(UUID departureId, PassengerHold hold) {
            return new HoldResponse(hold.id(), departureId, hold.fromSequence(), hold.toSequence(),
                    hold.passengerCount(), hold.expiresAt(), hold.status().name(),
                    hold.createdAt(), hold.updatedAt());
        }
    }
}
