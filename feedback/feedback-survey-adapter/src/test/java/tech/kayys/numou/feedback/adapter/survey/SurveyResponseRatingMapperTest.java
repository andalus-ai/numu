package tech.kayys.numou.feedback.adapter.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.domain.rating.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.response.Response;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SurveyResponseRatingMapperTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Test
    void mapsSubmittedNpsAnswerIntoFeedbackRating() {
        TenantId tenant = TenantId.generate();
        QuestionId question = QuestionId.of("nps");
        Response response = Response.start(ResponseId.of("response-1"), tenant,
                SurveyId.of("survey-nps"), "respondent-1", 1, NOW);
        response.record(question, new AnswerValue.Numeric(10));
        response.submit(null, NOW.plusSeconds(30));

        var rating = new SurveyResponseRatingMapper().map(tenant, response, question,
                new RatingTarget("service", "support-case-1", "branch-1"),
                RatingMetric.NPS, RatingScale.of(0, 10), NOW.plusSeconds(31));

        assertEquals(RatingMetric.NPS, rating.metric());
        assertEquals(10, rating.value().intValueExact());
        assertEquals("survey-nps", rating.surveyReference());
        assertEquals("respondent-1", rating.respondent().actorId());
    }

    @Test
    void rejectsUnsubmittedOrCrossTenantResponses() {
        TenantId tenant = TenantId.generate();
        QuestionId question = QuestionId.of("csat");
        Response response = Response.start(ResponseId.of("response-2"), tenant,
                SurveyId.of("survey-csat"), null, 1, NOW);
        response.record(question, new AnswerValue.Numeric(5));
        var mapper = new SurveyResponseRatingMapper();
        var target = new RatingTarget("service", "case-2", null);
        assertThrows(IllegalArgumentException.class, () -> mapper.map(tenant, response, question,
                target, RatingMetric.CSAT, RatingScale.of(1, 5), NOW));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(TenantId.generate(), response, question,
                target, RatingMetric.CSAT, RatingScale.of(1, 5), NOW));
    }
}