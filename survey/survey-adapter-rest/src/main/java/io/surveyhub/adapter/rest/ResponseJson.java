package io.surveyhub.adapter.rest;

import io.surveyhub.application.port.in.ResponseUseCase.ResponseSession;
import io.surveyhub.application.port.in.ResponseUseCase.SubmissionResult;
import io.surveyhub.domain.quiz.ScoreResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Respondent-facing JSON. Never includes answer keys, scoring or explanations. */
final class ResponseJson {
    private ResponseJson() {}

    static Map<String, Object> session(ResponseSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id().value());
        m.put("surveyId", s.surveyId().value());
        m.put("status", s.status());
        m.put("attempt", s.attempt());
        m.put("locale", s.locale());
        s.deadline().ifPresent(d -> m.put("deadline", d.toString()));
        s.message().ifPresent(msg -> m.put("message", msg));
        m.put("questions", s.visibleQuestions().stream().map(q -> {
            Map<String, Object> qm = new LinkedHashMap<>(SurveyJson.renderQuestion(q, false));
            var sec = s.sections().get(q.id());
            if (sec != null) qm.put("section", Map.of("id", sec.id().value(), "title", sec.title()));
            return qm;
        }).toList());
        Map<String, Object> answers = new LinkedHashMap<>();
        s.answers().forEach((k, v) -> answers.put(k.value(), SurveyJson.renderAnswer(v)));
        m.put("answers", answers);
        return m;
    }

    static Map<String, Object> submission(SubmissionResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.id().value());
        out.put("thankYouMessage", r.thankYouMessage());
        r.score().ifPresent(s -> out.put("score", score(s)));
        return out;
    }

    static Map<String, Object> score(ScoreResult s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("earned", s.earned());
        m.put("max", s.max());
        m.put("percent", s.percent());
        m.put("passed", s.passed());
        if (!s.band().isEmpty()) { m.put("band", s.band()); m.put("bandMessage", s.bandMessage()); }
        m.put("items", s.items().stream().map(i -> Map.of("question", i.question().value(), "earned", i.earned(),
                "max", i.max(), "correct", i.fullyCorrect())).toList());
        return m;
    }
}
