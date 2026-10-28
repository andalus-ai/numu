package tech.kayys.numou.transport.runtime;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.transport.application.TransportCheckoutService;
import tech.kayys.numou.transport.domain.TransportCheckout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Optional, authenticated HTTP surface for the Transport + Payment checkout saga. */
@Authenticated
@Blocking
@ApplicationScoped
@IfBuildProperty(name = "numou.transport.checkout.enabled", stringValue = "true")
@Path("/v1/transport")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TransportCheckoutResource {
    @Inject
    TransportCheckoutService checkouts;

    @Inject
    SecurityIdentity identity;

    @Inject
    TransportBookingAuthorization authorization;

    @ConfigProperty(name = "numou.security.tenant-claim", defaultValue = "numou_tenant_id")
    String tenantAttribute;

    @ConfigProperty(name = "numou.security.user-id-claim", defaultValue = "numou_user_id")
    String actorAttribute;

    @POST
    @Path("/departures/{departureId}/checkouts")
    public Response start(@PathParam("departureId") UUID departureId,
                          @HeaderParam("Idempotency-Key") String idempotencyKey,
                          CreateCheckoutRequest request) {
        TransportIdentityContext caller = caller();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BadRequestException("Idempotency-Key must contain 1-128 characters");
        }
        if (request == null || request.fromSequence() == null || request.toSequence() == null
                || request.passengerCount() == null) {
            throw new BadRequestException("fromSequence, toSequence and passengerCount are required");
        }
        authorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.CREATE_CHECKOUT, departureId, null);
        TransportCheckout checkout = checkouts.start(new TransportCheckoutService.StartCommand(
                caller.tenantId(), idempotencyKey, departureId, request.fromSequence(),
                request.toSequence(), request.passengerCount()));
        return Response.accepted(CheckoutResponse.from(checkout)).build();
    }

    @GET
    @Path("/checkouts/{checkoutId}")
    public CheckoutResponse get(@PathParam("checkoutId") UUID checkoutId) {
        TransportIdentityContext caller = caller();
        authorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.READ_CHECKOUT, null, checkoutId);
        return CheckoutResponse.from(checkouts.get(caller.tenantId(), checkoutId));
    }

    @POST
    @Path("/checkouts/{checkoutId}/refresh")
    public CheckoutResponse refresh(@PathParam("checkoutId") UUID checkoutId) {
        TransportIdentityContext caller = caller();
        authorization.requireAllowed(caller, identity.getPrincipal().getName(),
                TransportBookingAuthorization.BookingOperation.REFRESH_CHECKOUT, null, checkoutId);
        return CheckoutResponse.from(checkouts.refresh(caller.tenantId(), checkoutId));
    }

    private TransportIdentityContext caller() {
        if (identity == null || identity.isAnonymous()) {
            throw new jakarta.ws.rs.NotAuthorizedException("Authentication is required");
        }
        return TransportIdentityContext.from(identity.getPrincipal().getName(),
                identity.getAttribute(tenantAttribute), identity.getAttribute(actorAttribute));
    }

    public record CreateCheckoutRequest(Integer fromSequence, Integer toSequence, Integer passengerCount) {
    }

    public record CheckoutResponse(UUID checkoutId, UUID departureId, UUID holdId, String paymentId,
                                   String paymentActionUri, BigDecimal amount, String currency,
                                   String status, Instant holdExpiresAt, Instant updatedAt) {
        static CheckoutResponse from(TransportCheckout checkout) {
            return new CheckoutResponse(checkout.checkoutId(), checkout.departureId(), checkout.holdId(),
                    checkout.paymentId(), checkout.paymentActionUri(), checkout.amount(), checkout.currency(),
                    checkout.status().name(), checkout.holdExpiresAt(), checkout.updatedAt());
        }
    }
}
