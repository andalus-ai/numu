package tech.kayys.numou.survey.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import tech.kayys.numou.survey.application.port.in.*;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase.*;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.SectionId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.survey.Survey;
import tech.kayys.numou.survey.domain.survey.SurveyKind;
import tech.kayys.numou.survey.domain.survey.SurveyStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** Authoring + lifecycle + results (management side). */
@Path("/api/v1/surveys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SurveyResource {
    private final SurveyAuthoringUseCase authoring;
    private final SurveyLifecycleUseCase lifecycle;
    private final ResultsUseCase results;
    private final RequestTenant tenant;

    @Inject
    public SurveyResource(SurveyAuthoringUseCase authoring, SurveyLifecycleUseCase lifecycle, ResultsUseCase results, RequestTenant tenant) {
        this.authoring = authoring;
        this.lifecycle = lifecycle;
        this.results = results;
        this.tenant = tenant;
    }

    @POST
    public Response create(JsonNode body) {
        SurveyKind kind = body.hasNonNull("kind") ? SurveyKind.valueOf(body.get("kind").asText().toUpperCase()) : SurveyKind.SURVEY;
        Survey s = authoring.createSurvey(tenant.get(), new CreateSurvey(body.path("title").asText(null), body.path("description").asText(""), kind));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @GET
    public List<Map<String, Object>> list(@QueryParam("status") String status) {
        SurveyStatus st = status == null ? null : SurveyStatus.valueOf(status.toUpperCase());
        return authoring.listSurveys(tenant.get(), st).stream().map(SurveyJson::renderSummary).toList();
    }

    @GET @Path("{id}")
    public Map<String, Object> get(@PathParam("id") String id) {
        return SurveyJson.render(authoring.getSurvey(tenant.get(), new SurveyId(id)));
    }

    @PUT @Path("{id}")
    public Map<String, Object> update(@PathParam("id") String id, JsonNode body) {
        var ctx = tenant.get();
        SurveyId sid = new SurveyId(id);
        Survey s = authoring.getSurvey(ctx, sid);
        if (body.has("title") || body.has("description"))
            s = authoring.updateDetails(ctx, sid, body.path("title").asText(s.title()), body.path("description").asText(s.description()));
        if (body.has("settings"))
            s = authoring.updateSettings(ctx, sid, SurveyJson.parseSettings(body.get("settings"), s.settings()));
        if (body.has("quiz"))
            s = authoring.configureQuiz(ctx, sid, SurveyJson.parseQuiz(body.get("quiz"), s.quizSettings().orElseThrow()));
        return SurveyJson.render(s);
    }

    @POST @Path("{id}/sections")
    public Response addSection(@PathParam("id") String id, JsonNode body) {
        Survey s = authoring.addSection(tenant.get(), new AddSection(new SurveyId(id), body.path("title").asText(null), body.path("description").asText("")));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @POST @Path("{id}/sections/{sectionId}/questions")
    public Response addQuestion(@PathParam("id") String id, @PathParam("sectionId") String sectionId, JsonNode body) {
        Survey s = authoring.addQuestion(tenant.get(),
                new AddQuestion(new SurveyId(id), new SectionId(sectionId), SurveyJson.parseQuestion(body, null)));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @PUT @Path("{id}/questions/{questionId}")
    public Map<String, Object> updateQuestion(@PathParam("id") String id, @PathParam("questionId") String qid, JsonNode body) {
        return SurveyJson.render(authoring.updateQuestion(tenant.get(), new SurveyId(id), SurveyJson.parseQuestion(body, qid)));
    }

    @DELETE @Path("{id}/questions/{questionId}")
    public Map<String, Object> removeQuestion(@PathParam("id") String id, @PathParam("questionId") String qid) {
        return SurveyJson.render(authoring.removeQuestion(tenant.get(), new SurveyId(id), new QuestionId(qid)));
    }

    @POST @Path("{id}/questions/{questionId}/move")
    public Map<String, Object> moveQuestion(@PathParam("id") String id, @PathParam("questionId") String qid, JsonNode body) {
        return SurveyJson.render(authoring.moveQuestion(tenant.get(),
                new MoveQuestion(new SurveyId(id), new QuestionId(qid), new SectionId(body.path("sectionId").asText(null)), body.path("index").asInt(Integer.MAX_VALUE))));
    }

    @GET @Path("{id}/readiness")
    public Map<String, Object> readiness(@PathParam("id") String id) {
        List<String> problems = lifecycle.checkReadiness(tenant.get(), new SurveyId(id));
        return Map.of("ready", problems.isEmpty(), "problems", problems);
    }

    @POST @Path("{id}/publish")
    public Map<String, Object> publish(@PathParam("id") String id) { return SurveyJson.render(lifecycle.publish(tenant.get(), new SurveyId(id))); }

    @POST @Path("{id}/close")
    public Map<String, Object> close(@PathParam("id") String id) { return SurveyJson.render(lifecycle.close(tenant.get(), new SurveyId(id))); }

    @POST @Path("{id}/archive")
    public Map<String, Object> archive(@PathParam("id") String id) { return SurveyJson.render(lifecycle.archive(tenant.get(), new SurveyId(id))); }

    @POST @Path("{id}/revise")
    public Response revise(@PathParam("id") String id) {
        return Response.status(201).entity(SurveyJson.render(lifecycle.revise(tenant.get(), new SurveyId(id)))).build();
    }

    @GET @Path("{id}/results")
    public Map<String, Object> results(@PathParam("id") String id) {
        var r = results.summary(tenant.get(), new SurveyId(id));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("surveyId", r.surveyId().value());
        out.put("totalSubmitted", r.totalSubmitted());
        out.put("questions", r.questions().stream().map(q -> Map.of("id", q.question().value(), "title", q.title(),
                "kind", q.kind(), "answered", q.answered(), "stats", q.stats())).toList());
        r.quiz().ifPresent(q -> out.put("quiz", q));
        return out;
    }
}
