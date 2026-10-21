package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.port.in.*;
import io.surveyhub.application.port.in.SurveyAuthoringUseCase.*;
import io.surveyhub.domain.id.QuestionId;
import io.surveyhub.domain.id.SectionId;
import io.surveyhub.domain.id.SurveyId;
import io.surveyhub.domain.survey.Survey;
import io.surveyhub.domain.survey.SurveyKind;
import io.surveyhub.domain.survey.SurveyStatus;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/** Authoring + lifecycle + results (management side). */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SurveyResource {
    private final SurveyAuthoringUseCase authoring;
    private final SurveyLifecycleUseCase lifecycle;
    private final RequestTenant tenant;

    @Inject
    public SurveyResource(SurveyAuthoringUseCase authoring, SurveyLifecycleUseCase lifecycle, RequestTenant tenant) {
        this.authoring = authoring;
        this.lifecycle = lifecycle;
        this.tenant = tenant;
    }

    @POST @Path("surveys")
    public Response create(JsonNode body) {
        SurveyKind kind = body.hasNonNull("kind") ? SurveyKind.valueOf(body.get("kind").asText().toUpperCase()) : SurveyKind.SURVEY;
        Survey s = authoring.createSurvey(tenant.get(), new CreateSurvey(body.path("title").asText(null), body.path("description").asText(""), kind));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @GET @Path("surveys")
    public List<Map<String, Object>> list(@QueryParam("status") String status) {
        SurveyStatus st = status == null ? null : SurveyStatus.valueOf(status.toUpperCase());
        return authoring.listSurveys(tenant.get(), st).stream().map(SurveyJson::renderSummary).toList();
    }

    @GET @Path("surveys/{id}")
    public Map<String, Object> get(@PathParam("id") String id) {
        return SurveyJson.render(authoring.getSurvey(tenant.get(), new SurveyId(id)));
    }

    @PUT @Path("surveys/{id}")
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

    /** Body: {"screenOuts":[{"when":<condition>,"message":"..."}],"quotas":[{"name","question","option","limit"}]} */
    @PUT @Path("surveys/{id}/eligibility")
    public Map<String, Object> eligibility(@PathParam("id") String id, JsonNode body) {
        return SurveyJson.render(authoring.configureEligibility(tenant.get(), new SurveyId(id), SurveyJson.parseEligibility(body)));
    }

    /** Body: {"indices":[{"name":"...","items":[{"question":"q","row":"(matrix only)","weight":1,"reverse":false}]}]} */
    @PUT @Path("surveys/{id}/indices")
    public Map<String, Object> indices(@PathParam("id") String id, JsonNode body) {
        return SurveyJson.render(authoring.configureIndices(tenant.get(), new SurveyId(id), SurveyJson.parseIndices(body.path("indices"))));
    }

    /** Body: {"defaultLocale":"en","locales":{"id":{"survey.title":"...","question.q1.title":"..."}}}. Unknown keys are rejected. */
    @PUT @Path("surveys/{id}/translations")
    public Map<String, Object> translations(@PathParam("id") String id, JsonNode body) {
        var tc = tenant.get();
        Survey current = authoring.getSurvey(tc, new SurveyId(id));
        return SurveyJson.render(authoring.configureTranslations(tc, current.id(), SurveyJson.parseTranslations(body, current.translations())));
    }

    /** Translatable texts (key -> source text) and, per language, coverage and the keys still missing. */
    @GET @Path("surveys/{id}/translations")
    public Map<String, Object> translationStatus(@PathParam("id") String id) {
        var st = authoring.translationStatus(tenant.get(), new SurveyId(id));
        Map<String, Object> locales = new java.util.LinkedHashMap<>();
        st.locales().forEach((l, c) -> locales.put(l, Map.of("translated", c.translated(), "total", c.total(), "percent", c.percent(), "missing", c.missing())));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("defaultLocale", st.defaultLocale());
        out.put("sources", st.sources());
        out.put("locales", locales);
        return out;
    }

    @POST @Path("surveys/{id}/sections")
    public Response addSection(@PathParam("id") String id, JsonNode body) {
        Survey s = authoring.addSection(tenant.get(), new AddSection(new SurveyId(id), body.path("title").asText(null), body.path("description").asText("")));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @POST @Path("surveys/{id}/sections/{sectionId}/questions")
    public Response addQuestion(@PathParam("id") String id, @PathParam("sectionId") String sectionId, JsonNode body) {
        Survey s = authoring.addQuestion(tenant.get(),
                new AddQuestion(new SurveyId(id), new SectionId(sectionId), SurveyJson.parseQuestion(body, null)));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @PUT @Path("surveys/{id}/questions/{questionId}")
    public Map<String, Object> updateQuestion(@PathParam("id") String id, @PathParam("questionId") String qid, JsonNode body) {
        return SurveyJson.render(authoring.updateQuestion(tenant.get(), new SurveyId(id), SurveyJson.parseQuestion(body, qid)));
    }

    @DELETE @Path("surveys/{id}/questions/{questionId}")
    public Map<String, Object> removeQuestion(@PathParam("id") String id, @PathParam("questionId") String qid) {
        return SurveyJson.render(authoring.removeQuestion(tenant.get(), new SurveyId(id), new QuestionId(qid)));
    }

    @POST @Path("surveys/{id}/questions/{questionId}/move")
    public Map<String, Object> moveQuestion(@PathParam("id") String id, @PathParam("questionId") String qid, JsonNode body) {
        return SurveyJson.render(authoring.moveQuestion(tenant.get(),
                new MoveQuestion(new SurveyId(id), new QuestionId(qid), new SectionId(body.path("sectionId").asText(null)), body.path("index").asInt(Integer.MAX_VALUE))));
    }

    @GET @Path("surveys/{id}/readiness")
    public Map<String, Object> readiness(@PathParam("id") String id) {
        List<String> problems = lifecycle.checkReadiness(tenant.get(), new SurveyId(id));
        return Map.of("ready", problems.isEmpty(), "problems", problems);
    }

    @POST @Path("surveys/{id}/publish")
    public Map<String, Object> publish(@PathParam("id") String id) { return SurveyJson.render(lifecycle.publish(tenant.get(), new SurveyId(id))); }

    @POST @Path("surveys/{id}/close")
    public Map<String, Object> close(@PathParam("id") String id) { return SurveyJson.render(lifecycle.close(tenant.get(), new SurveyId(id))); }

    @POST @Path("surveys/{id}/archive")
    public Map<String, Object> archive(@PathParam("id") String id) { return SurveyJson.render(lifecycle.archive(tenant.get(), new SurveyId(id))); }

    @POST @Path("surveys/{id}/revise")
    public Response revise(@PathParam("id") String id) {
        return Response.status(201).entity(SurveyJson.render(lifecycle.revise(tenant.get(), new SurveyId(id)))).build();
    }
}
