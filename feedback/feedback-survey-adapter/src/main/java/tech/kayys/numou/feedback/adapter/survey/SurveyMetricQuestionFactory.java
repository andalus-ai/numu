package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.question.QuestionType;

import java.util.Objects;

/**
 * Translates Feedback metric semantics into the existing Survey question model.
 * Survey remains the owner of authoring, branching, publication, respondent sessions and scoring.
 */
public final class SurveyMetricQuestionFactory {
    public Question requiredQuestion(RatingMetric metric, String questionId, String prompt) {
        return requiredQuestion(metric, questionId, prompt, null, null);
    }

    public Question requiredQuestion(RatingMetric metric, String questionId, String prompt,
                                     Integer customMin, Integer customMax) {
        Objects.requireNonNull(metric, "metric cannot be null");
        QuestionType type = switch (metric) {
            case NPS -> new QuestionType.Scale(0, 10, "Not at all likely", "Extremely likely");
            case CSAT, STAR_RATING -> new QuestionType.Rating(5);
            case CES -> new QuestionType.Scale(1, 7, "Strongly disagree", "Strongly agree");
            case CUSTOM -> {
                if (customMin == null || customMax == null || customMin >= customMax) {
                    throw new IllegalArgumentException("CUSTOM metric requires customMin < customMax");
                }
                yield new QuestionType.Scale(customMin, customMax, "Minimum", "Maximum");
            }
        };
        return Question.of(questionId, prompt, type).mandatory();
    }
}