package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.port.in.ResponseUseCase;
import io.surveyhub.application.port.in.ResponseUseCase.SaveAnswer;
import io.surveyhub.application.port.in.ResponseUseCase.StartResponse;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.id.ResponseId;
import io.surveyhub.domain.id.SurveyId;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/** Authenticated respondent flow (tenant API). For anonymous share links see {@link PublicResource}. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResponseResource {
    private final ResponseUseCase responses;
    private final RequestTenant tenant;

    @Inject
    public ResponseResource(ResponseUseCase responses, RequestTenant tenant) {
        this.responses = responses;
        this.tenant = tenant;
    }

    @POST @Path("surveys/{id}/responses")
    public Response start(@PathParam("id") String id, JsonNode body) {
        String respondent = body == null ? null : body.path("respondentId").asText(null);
        return Response.status(201).entity(ResponseJson.session(
                responses.start(tenant.get(), new StartResponse(new SurveyId(id), respondent, java.util.Map.of(),
                        body == null ? null : body.path("locale").asText(null))))).build();
    }

    @GET @Path("responses/{rid}")
    public Map<String, Object> session(@PathParam("rid") String rid) {
        return ResponseJson.session(responses.getSession(tenant.get(), new ResponseId(rid)));
    }

    /** Body: an answer object, e.g. {"choice":["a"]}; an empty object clears the answer. */
    @PUT @Path("responses/{rid}/answers/{qid}")
    public Map<String, Object> answer(@PathParam("rid") String rid, @PathParam("qid") String qid, JsonNode body) {
        return ResponseJson.session(responses.saveAnswer(tenant.get(),
                new SaveAnswer(new ResponseId(rid), new QuestionId(qid), SurveyJson.parseAnswer(body))));
    }

    @POST @Path("responses/{rid}/submit")
    public Map<String, Object> submit(@PathParam("rid") String rid) {
        return ResponseJson.submission(responses.submit(tenant.get(), new ResponseId(rid)));
    }
}
