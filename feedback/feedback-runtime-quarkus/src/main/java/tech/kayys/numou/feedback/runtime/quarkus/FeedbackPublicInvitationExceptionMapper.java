package tech.kayys.numou.feedback.runtime.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;

/** Stable, non-sensitive error contract for public invitation callers. */
@Provider
@ApplicationScoped
public class FeedbackPublicInvitationExceptionMapper implements ExceptionMapper<RuntimeException> {
    private static final Logger LOG = Logger.getLogger(FeedbackPublicInvitationExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException failure) {
        if (failure instanceof WebApplicationException web) {
            return Response.fromResponse(web.getResponse()).header("Cache-Control", "no-store").build();
        }
        if (failure instanceof SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException)
            return error(404, "invitation_not_found", "The invitation is invalid, revoked or expired.");
        if (failure instanceof SubmitDistributedSurveyResponseHandler.SurveyDistributionLimitReachedException)
            return error(409, "invitation_response_limit_reached", "This invitation has reached its response limit.");
        if (failure instanceof PublicInvitationRateLimitExceededException)
            return Response.status(429).header("Retry-After", "60")
                    .entity(new ApiError("rate_limit_exceeded", "Too many requests for this invitation."))
                    .type(MediaType.APPLICATION_JSON_TYPE).header("Cache-Control", "no-store").build();
        if (failure instanceof DomainException domain) {
            return switch (domain.code()) {
                case NOT_FOUND -> error(404, "response_not_found", "The response was not found for this invitation.");
                case FORBIDDEN -> error(403, "forbidden", "This operation is not permitted.");
                case VALIDATION_FAILED -> error(422, "invalid_response", "The response contains invalid or incomplete answers.");
                case LIMIT_EXCEEDED -> error(409, "survey_limit_reached", "The survey cannot accept another response.");
                case INVALID_STATE, CONFLICT, TIME_LIMIT_EXCEEDED ->
                        error(409, "response_state_conflict", "The response cannot make that state transition.");
            };
        }
        if (failure instanceof IllegalArgumentException)
            return error(400, "invalid_request", "The request is invalid.");
        if (failure instanceof SecurityException)
            return error(403, "forbidden", "This operation is not permitted.");
        if (failure instanceof IllegalStateException)
            return error(409, "response_state_conflict", "The response cannot make that state transition.");

        LOG.error("Unhandled public Feedback invitation API failure", failure);
        return error(500, "internal_error", "The request could not be completed.");
    }

    private static Response error(int status, String code, String message) {
        return Response.status(status).entity(new ApiError(code, message))
                .type(MediaType.APPLICATION_JSON_TYPE).header("Cache-Control", "no-store").build();
    }

    public record ApiError(String code, String message) { }
}