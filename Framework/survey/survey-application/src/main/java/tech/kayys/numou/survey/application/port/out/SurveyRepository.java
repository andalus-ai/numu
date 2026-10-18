package tech.kayys.numou.survey.application.port.out;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.survey.Survey;
import tech.kayys.numou.survey.domain.survey.SurveyStatus;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped persistence port for survey aggregates. */
public interface SurveyRepository {
    Optional<Survey> findById(TenantId tenantId, SurveyId surveyId);

    /** Lists surveys belonging to a tenant, optionally filtered by lifecycle status. */
    List<Survey> findByTenant(TenantId tenantId, SurveyStatus statusOrNull);

    void save(Survey survey);

    long countByStatus(TenantId tenantId, SurveyStatus status);
}
