package tech.kayys.numou.feedback.runtime.quarkus;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.feedback.application.service.PublicInvitationRateLimiter;
import tech.kayys.numou.feedback.application.service.PublicSurveyInvitationService;

import java.time.Clock;
import java.util.Map;

/**
 * Anonymous respondent API. The opaque invitation token is the only authorization
 * credential; every operation re-resolves it and scopes Survey access to its tenant.
 */
@Blocking
@ApplicationScoped
@Path("/api/v1/feedback/public/invitations")
@Produces(MediaType.APPLICATION_JSON)
public class PublicFeedbackInvitationResource {
    private final PublicSurveyInvitationService invitations;
    private final PublicInvitationRateLimiter rateLimiter;
    private final Clock clock;
    private final boolean enabled;

    @Inject
    public PublicFeedbackInvitationResource(PublicSurveyInvitationService invitations,
            PublicInvitationRateLimiter rateLimiter, Clock clock,
            @ConfigProperty(name = "numou.feedback.public-invitations.enabled", defaultValue = "true")
            boolean enabled) {
        this.invitations = invitations;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.enabled = enabled;
    }

    @GET
    @Path("/{token}")
    public Response describe(@PathParam("token") String token) {
        admit(token);
        return noStore(Response.ok(invitations.describe(token))).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{token}/responses")
    public Response start(@PathParam("token") String token, StartRequest request) {
        admit(token);
        if (request == null) throw new jakarta.ws.rs.BadRequestException("Request body is required");
        if (request.respondentId() != null && request.respondentId().length() > 200)
            throw new jakarta.ws.rs.BadRequestException("respondentId exceeds 200 characters");
        return noStore(Response.status(Response.Status.CREATED)
                .entity(invitations.start(token, request.respondentId()))).build();
    }

    @GET
    @Path("/{token}/responses/{responseId}")
    public Response getSession(@PathParam("token") String token,
                               @PathParam("responseId") String responseId) {
        admit(token);
        return noStore(Response.ok(invitations.getSession(token, responseId))).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{token}/responses/{responseId}/answers/{questionId}")
    public Response saveAnswer(@PathParam("token") String token,
                               @PathParam("responseId") String responseId,
                               @PathParam("questionId") String questionId,
                               Map<String, Object> answer) {
        admit(token);
        if (answer == null) throw new jakarta.ws.rs.BadRequestException("Answer body is required");
        return noStore(Response.ok(invitations.saveAnswer(token, responseId, questionId, answer))).build();
    }

    @POST
    @Path("/{token}/responses/{responseId}/submit")
    public Response submit(@PathParam("token") String token,
                           @PathParam("responseId") String responseId) {
        admit(token);
        var outcome = invitations.submit(token, responseId, clock.instant());
        return noStore(Response.ok(Map.of("responseId", responseId, "status", outcome.name()))).build();
    }

    private void admit(String token) {
        if (!enabled) throw new jakarta.ws.rs.NotFoundException();
        // Validate before creating a rate-limit row, so random tokens cannot fill the limiter table.
        invitations.assertValidInvitation(token);
        if (!rateLimiter.allow(token, clock.instant())) throw new PublicInvitationRateLimitExceededException();
    }

    private static Response.ResponseBuilder noStore(Response.ResponseBuilder response) {
        return response.header("Cache-Control", "no-store, private")
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer");
    }

    public record StartRequest(String respondentId) { }
}