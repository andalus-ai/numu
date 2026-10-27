package tech.kayys.numou.workspace.adapter.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.numou.workspace.application.WorkspaceApi;

/** Maps the transport-neutral workspace error contract to HTTP without exposing stack traces. */
@Provider
public class WorkspaceApiExceptionMapper implements ExceptionMapper<WorkspaceApi.ApiException> {
    @Override
    public Response toResponse(WorkspaceApi.ApiException exception) {
        int status = exception.status();
        String message = status >= 500 ? "Workspace request failed" : exception.getMessage();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorResponse(status, message))
                .build();
    }

    public record ErrorResponse(int status, String message) {}
}
