package tech.kayys.numou.survey.application.port.in;

import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;
import tech.kayys.numou.survey.domain.response.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Filling in a published survey / taking a quiz. */
public interface ResponseUseCase {
    ResponseSession start(TenantContext ctx, StartResponse cmd);

    /** Save (or, with an empty answer, clear) one answer; returns the refreshed session (visibility may have changed). */
    ResponseSession saveAnswer(TenantContext ctx, SaveAnswer cmd);

    ResponseSession getSession(TenantContext ctx, ResponseId id);

    SubmissionResult submit(TenantContext ctx, ResponseId id);

    /** @param respondentId null/blank allowed only for anonymous surveys */
    record StartResponse(SurveyId survey, String respondentId) {}

    record SaveAnswer(ResponseId response, QuestionId question, AnswerValue answer) {}

    /** @param deadline empty for untimed surveys */
    record ResponseSession(ResponseId id, SurveyId surveyId, ResponseStatus status, int attempt,
                           List<Question> visibleQuestions, Map<QuestionId, AnswerValue> answers,
                           Optional<Instant> deadline) {}

    /** @param score empty for plain surveys or when the quiz hides results */
    record SubmissionResult(ResponseId id, Optional<ScoreResult> score, String thankYouMessage) {}
}
