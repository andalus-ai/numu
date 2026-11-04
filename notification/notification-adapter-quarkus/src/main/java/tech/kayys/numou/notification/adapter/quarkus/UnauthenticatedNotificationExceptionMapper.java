package tech.kayys.numou.notification.adapter.quarkus;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.notification.application.AuthenticatedNotificationApi;

/** Maps missing verified notification actor context to a non-disclosing HTTP 401. */
@Provider
public class UnauthenticatedNotificationExceptionMapper
        implements ExceptionMapper<AuthenticatedNotificationApi.UnauthenticatedNotificationRequestException> {
    @Override
    public Response toResponse(AuthenticatedNotificationApi.UnauthenticatedNotificationRequestException failure) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("unauthenticated", "Authentication is required"))
                .build();
    }

    public record ErrorResponse(String code, String message) {
    }
}
