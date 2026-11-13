package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface SurveyDistributionRepository {
    void save(SurveyDistribution distribution);
    Optional<SurveyDistribution> findById(TenantId tenantId, UUID id);
    Optional<SurveyDistribution> findActiveByTokenHash(String tokenHash);
}
