package tech.kayys.numou.feedback.adapter.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.survey.domain.question.QuestionType;

import static org.junit.jupiter.api.Assertions.*;

class SurveyMetricTemplateFactoryTest {
    private final SurveyMetricTemplateFactory factory = new SurveyMetricTemplateFactory();

    @Test
    void createsReusableNpsTemplateThatCanInstantiateSurvey() {
        var template = factory.create(RatingMetric.NPS, 3);
        var survey = template.instantiate(
                tech.kayys.numou.survey.domain.id.SurveyId.of("nps-run-1"),
                tech.kayys.numou.foundation.domain.tenant.TenantId.generate(),
                java.time.Instant.parse("2026-11-11T10:00:00Z"));
        assertEquals("feedback-nps", template.code());
        assertEquals(3, template.version());
        assertEquals(1, survey.questionCount());
        assertInstanceOf(QuestionType.Scale.class, survey.questions().getFirst().type());
    }

    @Test
    void createsCsatAndCesTemplates() {
        assertEquals("feedback-csat", factory.create(RatingMetric.CSAT, 1).code());
        assertEquals("feedback-ces", factory.create(RatingMetric.CES, 1).code());
        assertThrows(IllegalArgumentException.class, () -> factory.create(RatingMetric.CUSTOM, 1));
    }
}