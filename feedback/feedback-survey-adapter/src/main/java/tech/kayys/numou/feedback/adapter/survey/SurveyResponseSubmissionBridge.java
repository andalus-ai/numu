package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.OptionId;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges invitation-authorized Feedback requests to Survey's own application use cases.
 * Survey remains authoritative for tenant checks, response state, answer validation and scoring.
 */
public final class SurveyResponseSubmissionBridge
        implements DistributedSurveyResponsePort, PublicSurveyAccessPort {
    private final ResponseUseCase responses;
    private final SurveyAuthoringUseCase surveys;
    private final Clock clock;

    /** Retained for callers that only need the original submit-only port. */
    public SurveyResponseSubmissionBridge(ResponseUseCase responses) {
        this(responses, null, Clock.systemUTC());
    }

    public SurveyResponseSubmissionBridge(ResponseUseCase responses, SurveyAuthoringUseCase surveys) {
        this(responses, surveys, Clock.systemUTC());
    }

    public SurveyResponseSubmissionBridge(ResponseUseCase responses, SurveyAuthoringUseCase surveys, Clock clock) {
        this.responses = Objects.requireNonNull(responses, "responses cannot be null");
        this.surveys = surveys;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public SurveyDescriptor describe(TenantId tenantId, String surveyId) {
        if (surveys == null) throw new IllegalStateException("Survey authoring query is not configured");
        var survey = surveys.getSurvey(context(tenantId), new SurveyId(surveyId));
        if (!survey.isAcceptingResponses(clock.instant())) {
            throw new IllegalStateException("Survey is not accepting public responses");
        }
        return new SurveyDescriptor(survey.id().value(), survey.title(), survey.description(),
                survey.status().name(), survey.settings().anonymous(), survey.settings().thankYouMessage(),
                survey.questionCount());
    }

    @Override
    public ResponseSnapshot start(TenantId tenantId, String surveyId, String respondentId) {
        return snapshot(responses.start(context(tenantId),
                new ResponseUseCase.StartResponse(new SurveyId(surveyId), respondentId)));
    }

    @Override
    public ResponseSnapshot getSession(TenantId tenantId, String responseId) {
        return snapshot(responses.getSession(context(tenantId), new ResponseId(responseId)));
    }

    @Override
    public ResponseSnapshot saveAnswer(TenantId tenantId, String responseId,
                                        String questionId, Map<String, Object> answer) {
        return snapshot(responses.saveAnswer(context(tenantId),
                new ResponseUseCase.SaveAnswer(new ResponseId(responseId),
                        new QuestionId(questionId), parseAnswer(answer))));
    }

    @Override
    public void submit(TenantId tenantId, String surveyId, String responseId) {
        TenantContext context = new TenantContext(tenantId, "public-invitation");
        ResponseId id = new ResponseId(responseId);
        ResponseUseCase.ResponseSession session = responses.getSession(context, id);
        if (!surveyId.equals(session.surveyId().value())) {
            throw new IllegalArgumentException("Response does not belong to the invited survey");
        }
        // A retried invitation submission may arrive after Survey committed but before
        // Feedback finalized its quota claim. Treat that state as successful/idempotent.
        if (session.status() == tech.kayys.numou.survey.domain.response.ResponseStatus.SUBMITTED) return;
        responses.submit(context, id);
    }
}
