package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.domain.id.ResponseId;

import java.util.Objects;

/**
 * Bridges a verified Feedback invitation to Survey's own submission use case.
 * It verifies the response belongs to the survey bound to the invitation before submit.
 */
public final class SurveyResponseSubmissionBridge implements DistributedSurveyResponsePort {
    private final ResponseUseCase responses;

    public SurveyResponseSubmissionBridge(ResponseUseCase responses) {
        this.responses = Objects.requireNonNull(responses, "responses cannot be null");
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
