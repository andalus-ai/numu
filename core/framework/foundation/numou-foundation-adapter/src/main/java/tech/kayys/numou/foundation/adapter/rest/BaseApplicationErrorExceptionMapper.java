package tech.kayys.numou.foundation.adapter.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;

/**
 * Reusable base JAX-RS ExceptionMapper that standardizes ApplicationError translation.
 */
public abstract class BaseApplicationErrorExceptionMapper
        implements ExceptionMapper<ApplicationErrorException> {

    @Override
    public Response toResponse(ApplicationErrorException exception) {
        var error = exception.error();
        var status = resolveHttpStatus(error.code());

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(error.code(), error.message(), status.getStatusCode()))
                .build();
    }

    protected Response.Status resolveHttpStatus(String errorCode) {
        if (errorCode.endsWith("_NOT_FOUND")) {
            return Response.Status.NOT_FOUND;
        }
        if (errorCode.endsWith("_CONFLICT") || errorCode.contains("ALREADY_EXISTS") || errorCode.contains("ALREADY_IN_USE")) {
            return Response.Status.CONFLICT;
        }
        if (errorCode.endsWith("_UNAUTHORIZED")) {
            return Response.Status.UNAUTHORIZED;
        }
        if (errorCode.endsWith("_FORBIDDEN")) {
            return Response.Status.FORBIDDEN;
        }
        return Response.Status.BAD_REQUEST;
    }
}
