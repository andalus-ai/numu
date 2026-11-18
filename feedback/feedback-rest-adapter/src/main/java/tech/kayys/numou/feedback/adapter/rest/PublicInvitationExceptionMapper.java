package tech.kayys.numou.feedback.adapter.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public final class PublicInvitationExceptionMapper
        implements ExceptionMapper<PublicSurveyInvitationResource.PublicInvitationNotFoundException> {
    @Override
    public Response toResponse(PublicSurveyInvitationResource.PublicInvitationNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "invitation_not_found"))
                .header("Cache-Control", "no-store").build();
    }
}
