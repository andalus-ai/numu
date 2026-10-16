package tech.kayys.syirkah.identity.adapter.inbound.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;

/**
 * The only place that translates a structured ApplicationError into
 * an HTTP status code. Handlers never know about HTTP; this mapper
 * never knows about business rules - just a code -> status lookup.
 */
@Provider
public class ApplicationErrorExceptionMapper
        implements ExceptionMapper<ApplicationErrorException> {

    @Override
    public Response toResponse(ApplicationErrorException exception) {
        var error = exception.error();

        var status = switch (error.code()) {
            case "USER_NOT_FOUND" -> Response.Status.NOT_FOUND;
            case "EMAIL_ALREADY_IN_USE" -> Response.Status.CONFLICT;
            default -> Response.Status.BAD_REQUEST;
        };

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorBody(error.code(), error.message()))
                .build();
    }

    record ErrorBody(String code, String message) {
    }

}
