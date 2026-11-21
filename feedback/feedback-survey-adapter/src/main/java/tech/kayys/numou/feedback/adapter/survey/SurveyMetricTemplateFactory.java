package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.survey.domain.id.SectionId;
import tech.kayys.numou.survey.domain.survey.*;

import java.util.List;
import java.util.Objects;

/** Builds reusable standard metric templates using Survey's own immutable question model. */
public final class SurveyMetricTemplateFactory {
    private final SurveyMetricQuestionFactory questions = new SurveyMetricQuestionFactory();

    public SurveyTemplate create(RatingMetric metric, int templateVersion) {
        Objects.requireNonNull(metric, "metric cannot be null");
        String code = "feedback-" + metric.name().toLowerCase(java.util.Locale.ROOT);
        String title = switch (metric) {
            case NPS -> "Net Promoter Score";
            case CSAT -> "Customer Satisfaction";
            case CES -> "Customer Effort Score";
            case STAR_RATING -> "Star Rating";
            case CUSTOM -> throw new IllegalArgumentException("Custom metrics require an explicitly configured template");
        };
        String prompt = switch (metric) {
            case NPS -> "How likely are you to recommend us to a friend or colleague?";
            case CSAT -> "How satisfied are you with your experience?";
            case CES -> "How easy was it to complete your task?";
            case STAR_RATING -> "How would you rate your experience?";
            case CUSTOM -> throw new IllegalArgumentException("Custom metrics require an explicitly configured template");
        };
        var question = questions.requiredQuestion(metric, "rating-" + metric.name().toLowerCase(java.util.Locale.ROOT), prompt);
        Section section = new Section(new SectionId("feedback-rating"), "Your experience", "", List.of(question));
        return new SurveyTemplate(code, templateVersion, title, "Reusable Feedback metric survey template",
                SurveyKind.SURVEY, List.of(section), SurveySettings.defaults());
    }
}