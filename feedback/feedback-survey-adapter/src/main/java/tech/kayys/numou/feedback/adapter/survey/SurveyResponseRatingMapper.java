package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.domain.FeedbackActor;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.response.Response;
import tech.kayys.numou.survey.domain.response.ResponseStatus;

import java.math.BigDecimal;
import java.util.Objects;

/** Converts a submitted numeric Survey answer into the Feedback rating contract. */
public final class SurveyResponseRatingMapper {
    public FeedbackRating map(TenantId tenantId, Response response, QuestionId questionId,
                              RatingTarget target, RatingMetric metric, RatingScale scale) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
        Objects.requireNonNull(questionId, "questionId cannot be null");
        if (!tenantId.equals(response.tenantId())) {
            throw new IllegalArgumentException("Survey response belongs to another tenant");
        }
        if (response.status() != ResponseStatus.SUBMITTED) {
            throw new IllegalArgumentException("Only submitted survey responses can be imported as ratings");
        }
        AnswerValue answer = response.answers().get(questionId);
        if (!(answer instanceof AnswerValue.Numeric numeric)) {
            throw new IllegalArgumentException("Survey answer is missing or is not numeric");
        }
        if (!Double.isFinite(numeric.value())) throw new IllegalArgumentException("Survey rating must be finite");
        FeedbackActor respondent = response.respondentId()
                .map(id -> new FeedbackActor("survey-respondent", id)).orElse(null);
        return FeedbackRating.submit(FeedbackRatingId.generate(), tenantId, target, metric, scale,
                BigDecimal.valueOf(numeric.value()), respondent, response.surveyId().value(),
                response.submittedAt().orElseThrow(() -> new IllegalArgumentException("Submitted response has no submittedAt timestamp")));
    }
}