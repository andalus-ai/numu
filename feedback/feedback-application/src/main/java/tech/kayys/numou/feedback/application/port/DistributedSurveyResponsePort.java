package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Implemented by the Survey adapter using Survey's existing response submission use case. */
@FunctionalInterface
public interface DistributedSurveyResponsePort {
    void submit(TenantId tenantId, String surveyId, String responseId);
}
