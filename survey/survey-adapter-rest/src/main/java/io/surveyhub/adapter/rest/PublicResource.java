package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.port.in.PublicResponseUseCase;
import io.surveyhub.application.port.in.PublicResponseUseCase.PublicSurvey;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.id.ResponseId;
import io.surveyhub.presentation.RespondentPage;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Respondent-facing, unauthenticated surface: the share token resolves survey and tenant.
 * Put rate limiting / bot protection (WAF, reverse proxy) in front of /p and /api/v1/public in production.
 */
@Path("/")
public class PublicResource {
    /** The page only needs its own origin; inline script/style are ours. Framing is allowed on purpose (embeds). */
    private static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
            + "img-src https: data:; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors *";

    private final PublicResponseUseCase pub;

    @Inject
    public PublicResource(PublicResponseUseCase pub) { this.pub = pub; }

    @GET @Path("p/{token}") @Produces(MediaType.TEXT_HTML)
    public Response page(@PathParam("token") String token) {
        PublicSurvey s = pub.describe(token);
        return Response.ok(RespondentPage.render(s.survey(), token))
                .header("Content-Security-Policy", CSP).header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff").build();
    }

    @GET @Path("api/v1/public/{token}") @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> meta(@PathParam("token") String token, @QueryParam("lang") String lang) {
        PublicSurvey s = pub.describe(token, lang);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", s.title());
        m.put("description", s.description());
        m.put("welcome", s.welcome());
        m.put("thankYou", s.thankYou());
        m.put("locale", s.locale());
        m.put("locales", s.locales());
        m.put("open", s.open());
        m.put("access", s.access());
        m.put("anonymous", s.survey().settings().anonymous());
        m.put("publicResults", s.survey().settings().publicResults());
        return m;
    }

    @POST @Path("api/v1/public/{token}/responses") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("token") String token, JsonNode body) {
        String respondent = body == null ? null : body.path("respondentId").asText(null);
        String invitation = body == null ? null : body.path("invitation").asText(null);
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>();
        if (body != null) body.path("metadata").fields().forEachRemaining(e -> metadata.put(e.getKey(), e.getValue().asText()));
        return Response.status(201).entity(ResponseJson.session(pub.start(token, respondent, invitation, metadata, body == null ? null : body.path("locale").asText(null)))).build();
    }

    @GET @Path("api/v1/public/{token}/responses/{rid}") @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> session(@PathParam("token") String token, @PathParam("rid") String rid) {
        return ResponseJson.session(pub.getSession(token, new ResponseId(rid)));
    }

    @PUT @Path("api/v1/public/{token}/responses/{rid}/answers/{qid}") @Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> answer(@PathParam("token") String token, @PathParam("rid") String rid,
                                      @PathParam("qid") String qid, JsonNode body) {
        return ResponseJson.session(pub.saveAnswer(token, new ResponseId(rid), new QuestionId(qid), SurveyJson.parseAnswer(body)));
    }

    @POST @Path("api/v1/public/{token}/responses/{rid}/submit") @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> submit(@PathParam("token") String token, @PathParam("rid") String rid) {
        return ResponseJson.submission(pub.submit(token, new ResponseId(rid)));
    }

    @GET @Path("api/v1/public/{token}/results") @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> results(@PathParam("token") String token) {
        return AnalyticsJson.results(pub.publicResults(token));
    }
}
