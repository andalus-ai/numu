package tech.kayys.numou.feedback.adapter.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public final class PublicSurveyInputExceptionMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "invalid_request"))
                .header("Cache-Control", "no-store").build();
    }
}
