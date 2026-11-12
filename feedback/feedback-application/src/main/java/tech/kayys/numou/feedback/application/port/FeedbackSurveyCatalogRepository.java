package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.survey.FeedbackSurveyTemplate;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

public interface FeedbackSurveyCatalogRepository {
    /** Inserts an immutable version; duplicate tenant/code/version is a conflict. */
    void save(FeedbackSurveyTemplate template);
    Optional<FeedbackSurveyTemplate> find(TenantId tenantId, String code, int version);
    Optional<FeedbackSurveyTemplate> findLatestPublished(TenantId tenantId, String code);
    List<FeedbackSurveyTemplate> list(TenantId tenantId, String code, int limit);
}
