package tech.kayys.syirkah.identity.adapter.inbound.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.syirkah.identity.application.port.AuthorizationException;

@Provider
public class AuthorizationExceptionMapper implements ExceptionMapper<AuthorizationException> {
    @Override
    public Response toResponse(AuthorizationException exception) {
        var unauthorized = exception.getMessage() != null
                && exception.getMessage().startsWith("Access denied: authentication.");
        var status = unauthorized ? Response.Status.UNAUTHORIZED : Response.Status.FORBIDDEN;
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(status.getStatusCode(), exception.getMessage()))
                .build();
    }

    public record ErrorResponse(int status, String message) {}
}
