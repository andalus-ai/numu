package tech.kayys.numou.survey.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase.*;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** Respondent side: start, answer one question at a time, submit. Answer keys/explanations are never sent. */
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
        return Response.status(201).entity(render(responses.start(tenant.get(), new StartResponse(new SurveyId(id), respondent)))).build();
    }

    @GET @Path("responses/{rid}")
    public Map<String, Object> session(@PathParam("rid") String rid) {
        return render(responses.getSession(tenant.get(), new ResponseId(rid)));
    }

    /** Body: an answer object, e.g. {"choice":["a"]}; an empty answer (or {}) clears it. */
    @PUT @Path("responses/{rid}/answers/{qid}")
    public Map<String, Object> answer(@PathParam("rid") String rid, @PathParam("qid") String qid, JsonNode body) {
        return render(responses.saveAnswer(tenant.get(),
                new SaveAnswer(new ResponseId(rid), new QuestionId(qid), SurveyJson.parseAnswer(body))));
    }

    @POST @Path("responses/{rid}/submit")
    public Map<String, Object> submit(@PathParam("rid") String rid) {
        SubmissionResult r = responses.submit(tenant.get(), new ResponseId(rid));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.id().value());
        out.put("thankYouMessage", r.thankYouMessage());
        r.score().ifPresent(s -> out.put("score", render(s)));
        return out;
    }

    private static Map<String, Object> render(ScoreResult s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("earned", s.earned());
        m.put("max", s.max());
        m.put("percent", s.percent());
        m.put("passed", s.passed());
        m.put("items", s.items().stream().map(i -> Map.of("question", i.question().value(), "earned", i.earned(),
                "max", i.max(), "correct", i.fullyCorrect())).toList());
        return m;
    }

    private static Map<String, Object> render(ResponseSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id().value());
        m.put("surveyId", s.surveyId().value());
        m.put("status", s.status());
        m.put("attempt", s.attempt());
        s.deadline().ifPresent(d -> m.put("deadline", d.toString()));
        m.put("questions", s.visibleQuestions().stream().map(q -> SurveyJson.renderQuestion(q, false)).toList());
        Map<String, Object> answers = new LinkedHashMap<>();
        s.answers().forEach((k, v) -> answers.put(k.value(), SurveyJson.renderAnswer(v)));
        m.put("answers", answers);
        return m;
    }
}
