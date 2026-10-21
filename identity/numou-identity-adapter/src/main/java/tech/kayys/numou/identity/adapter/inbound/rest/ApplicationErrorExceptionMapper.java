package tech.kayys.numou.identity.adapter.inbound.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;

/**
 * The only place that translates a structured ApplicationError into
 * an HTTP status code. Handlers never know about HTTP; this mapper
 * never knows about business rules - just a code -> status lookup.
 *
 * <p>Error codes starting with {@code SCIM_} are rendered in the SCIM 2.0
 * error response format (RFC 7644 \u00a73.5); all other codes use the
 * internal {@code ErrorBody} format.
 */
@Provider
public class ApplicationErrorExceptionMapper
        implements ExceptionMapper<ApplicationErrorException> {

    @Override
    public Response toResponse(ApplicationErrorException exception) {
        var error = exception.error();

        var status = switch (error.code()) {
            case "USER_NOT_FOUND", "SCIM_USER_NOT_FOUND", "IDENTITY_PROVIDER_NOT_FOUND"
                 -> Response.Status.NOT_FOUND;
            case "EMAIL_ALREADY_IN_USE" -> Response.Status.CONFLICT;
            case "IDENTITY_PROVIDER_DISABLED" -> Response.Status.FORBIDDEN;
            default -> Response.Status.BAD_REQUEST;
        };

        var entity = error.code().startsWith("SCIM_")
                ? ScimError.of(status.getStatusCode(), scimType(error.code()), error.message())
                : new ErrorBody(error.code(), error.message());

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(entity)
                .build();
    }

    private static String scimType(String code) {
        return switch (code) {
            case "SCIM_USER_NOT_FOUND" -> "notFound";
            case "SCIM_PATCH_UNSUPPORTED" -> "unsupported";
            case "SCIM_PATCH_INVALID" -> "invalidValue";
            case "SCIM_EMAIL_INVALID" -> "invalidValue";
            default -> "invalidValue";
        };
    }

    record ErrorBody(String code, String message) {
    }

}
