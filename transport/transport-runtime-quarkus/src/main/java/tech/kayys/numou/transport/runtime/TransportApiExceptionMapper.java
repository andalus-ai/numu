package tech.kayys.numou.transport.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import tech.kayys.numou.transport.application.TransportCheckoutIdempotencyConflictException;
import tech.kayys.numou.transport.spi.TransportCheckoutConcurrencyException;

/** Maps application/domain failures to stable, non-sensitive HTTP error codes. */
@Provider
@ApplicationScoped
public class TransportApiExceptionMapper implements ExceptionMapper<RuntimeException> {
    private static final Logger LOG = Logger.getLogger(TransportApiExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException failure) {
        if (failure instanceof WebApplicationException webFailure) {
            return webFailure.getResponse();
        }
        if (failure instanceof TransportCheckoutConcurrencyException
                || failure instanceof TransportCheckoutIdempotencyConflictException) {
            return error(Response.Status.CONFLICT, "checkout_conflict",
                    "The checkout changed concurrently or the idempotency key was reused.");
        }
        if (failure instanceof TransportAuthorizationUnavailableException) {
            return error(Response.Status.SERVICE_UNAVAILABLE, "authorization_unavailable",
                    "Tenant membership or authorization service is temporarily unavailable.");
        }
        if (failure instanceof SecurityException) {
            return error(Response.Status.FORBIDDEN, "trusted_tenant_context_required",
                    "Authenticated tenant and actor context is required.");
        }

        String type = failure.getClass().getSimpleName();
        if (type.equals("CapacityUnavailableException")) {
            return error(Response.Status.CONFLICT, "capacity_unavailable",
                    "Requested passenger capacity is not available.");
        }
        if (type.equals("IdempotencyConflictException")) {
            return error(Response.Status.CONFLICT, "idempotency_key_reused",
                    "Idempotency-Key was already used with a different request.");
        }
        if (failure instanceof IllegalArgumentException) {
            String message = failure.getMessage();
            if (message != null && (message.contains("no passenger inventory")
                    || message.contains("unknown passenger hold")
                    || message.contains("checkout not found")
                    || message.contains("payment was not found"))) {
                return error(Response.Status.NOT_FOUND, "booking_resource_not_found",
                        "Departure inventory or passenger hold was not found.");
            }
            return error(Response.Status.BAD_REQUEST, "invalid_booking_request",
                    "The booking request is invalid.");
        }
        if (failure instanceof IllegalStateException) {
            return error(Response.Status.CONFLICT, "booking_state_conflict",
                    "The booking cannot make that state transition.");
        }

        LOG.error("Unhandled Transport booking API failure", failure);
        return error(Response.Status.INTERNAL_SERVER_ERROR, "internal_error",
                "The request could not be completed.");
    }

    private static Response error(Response.Status status, String code, String message) {
        return Response.status(status).entity(new ApiError(code, message)).type("application/json").build();
    }

    public record ApiError(String code, String message) {
    }
}
