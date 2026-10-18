package tech.kayys.numou.survey.application.port.out;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.response.Response;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for respondent submissions. */
public interface ResponseRepository {
    Optional<Response> findById(TenantId tenantId, ResponseId responseId);

    /** Returns submitted responses for a survey within the specified tenant. */
    List<Response> findSubmitted(TenantId tenantId, SurveyId surveyId);

    void save(Response response);

    long countSubmitted(TenantId tenantId, SurveyId surveyId);

    long countByRespondent(TenantId tenantId, SurveyId surveyId, String respondentId);
}
