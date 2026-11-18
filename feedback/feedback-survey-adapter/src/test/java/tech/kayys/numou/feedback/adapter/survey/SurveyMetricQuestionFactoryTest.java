package tech.kayys.numou.feedback.adapter.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.rating.RatingMetric;
import tech.kayys.numou.survey.domain.question.QuestionType;

import static org.junit.jupiter.api.Assertions.*;

class SurveyMetricQuestionFactoryTest {
    private final SurveyMetricQuestionFactory factory = new SurveyMetricQuestionFactory();

    @Test
    void mapsNpsToCanonicalZeroToTenScale() {
        var question = factory.requiredQuestion(RatingMetric.NPS, "nps", "How likely are you to recommend us?");
        assertTrue(question.required());
        assertInstanceOf(QuestionType.Scale.class, question.type());
        var scale = (QuestionType.Scale) question.type();
        assertEquals(0, scale.min());
        assertEquals(10, scale.max());
    }

    @Test
    void mapsCsatAndCesToTheirCanonicalScales() {
        var csat = factory.requiredQuestion(RatingMetric.CSAT, "csat", "How satisfied are you?");
        assertEquals(5, ((QuestionType.Rating) csat.type()).max());

        var ces = factory.requiredQuestion(RatingMetric.CES, "ces", "How easy was it?");
        var scale = (QuestionType.Scale) ces.type();
        assertEquals(1, scale.min());
        assertEquals(7, scale.max());
    }

    @Test
    void customMetricRequiresExplicitScale() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.requiredQuestion(RatingMetric.CUSTOM, "custom", "Rate this"));
        var question = factory.requiredQuestion(RatingMetric.CUSTOM, "custom", "Rate this", 2, 8);
        var scale = (QuestionType.Scale) question.type();
        assertEquals(2, scale.min());
        assertEquals(8, scale.max());
    }
}