package tech.kayys.numou.feedback.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;

import java.time.Instant;
import java.util.Map;

/**
 * Public respondent API. The bearer invitation is the only source of tenant/survey
 * context; caller-supplied tenant and survey identifiers are never trusted.
 */
@Path("/api/v1/public/feedback/surveys")
@Produces(MediaType.APPLICATION_JSON)
public final class PublicSurveyInvitationResource {
    private final ResolveSurveyDistributionHandler invitations;
    private final ResponseUseCase responses;
    private final SubmitDistributedSurveyResponseHandler submissions;

    @Inject
    public PublicSurveyInvitationResource(ResolveSurveyDistributionHandler invitations,
            ResponseUseCase responses, SubmitDistributedSurveyResponseHandler submissions) {
        this.invitations = invitations;
        this.responses = responses;
        this.submissions = submissions;
    }

    @POST
    @Path("/{token}/responses")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("token") String token, JsonNode body) {
        SurveyDistribution grant = resolve(token);
        String respondentId = body == null ? null : body.path("respondentId").asText(null);
        var session = responses.start(context(grant), new ResponseUseCase.StartResponse(
                new SurveyId(grant.surveyId()), respondentId));
        return Response.status(Response.Status.CREATED).entity(session)
                .header("Cache-Control", "no-store").build();
    }

    @GET
    @Path("/{token}/responses/{responseId}")
    public Response session(@PathParam("token") String token, @PathParam("responseId") String responseId) {
        SurveyDistribution grant = resolve(token);
        var session = responses.getSession(context(grant), new ResponseId(responseId));
        if (!grant.surveyId().equals(session.surveyId().value())) return notFound();
        return Response.ok(session).header("Cache-Control", "no-store").build();
    }

    @PUT
    @Path("/{token}/responses/{responseId}/answers/{questionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response answer(@PathParam("token") String token, @PathParam("responseId") String responseId,
                           @PathParam("questionId") String questionId, JsonNode body) {
        SurveyDistribution grant = resolve(token);
        ResponseId id = new ResponseId(responseId);
        var existing = responses.getSession(context(grant), id);
        if (!grant.surveyId().equals(existing.surveyId().value())) return notFound();
        var updated = responses.saveAnswer(context(grant), new ResponseUseCase.SaveAnswer(
                id, new QuestionId(questionId), SurveyAnswerJson.parse(body)));
        return Response.ok(updated).header("Cache-Control", "no-store").build();
    }

    @POST
    @Path("/{token}/responses/{responseId}/submit")
    public Response submit(@PathParam("token") String token, @PathParam("responseId") String responseId) {
        try {
            var outcome = submissions.handle(token, responseId, Instant.now());
            if (outcome == SubmitDistributedSurveyResponseHandler.Outcome.SUBMISSION_IN_PROGRESS)
                return Response.status(Response.Status.CONFLICT).entity(Map.of("status", "submission_in_progress")).build();
            return Response.ok(Map.of("status", outcome.name().toLowerCase())).header("Cache-Control", "no-store").build();
        } catch (SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException e) {
            return notFound();
        } catch (SubmitDistributedSurveyResponseHandler.SurveyDistributionLimitReachedException e) {
            return Response.status(Response.Status.GONE).entity(Map.of("status", "response_limit_reached")).build();
        }
    }

    private SurveyDistribution resolve(String token) {
        return invitations.handle(token).orElseThrow(PublicInvitationNotFoundException::new);
    }

    private static TenantContext context(SurveyDistribution grant) {
        return new TenantContext(grant.tenantId(), "public-survey-invitation");
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "invitation_not_found"))
                .header("Cache-Control", "no-store").build();
    }

    @Provider
    public static final class PublicInvitationNotFoundMapper
            implements jakarta.ws.rs.ext.ExceptionMapper<PublicInvitationNotFoundException> {
        @Override public Response toResponse(PublicInvitationNotFoundException exception) {
            return notFound();
        }
    }

    public static final class PublicInvitationNotFoundException extends RuntimeException {}
}
