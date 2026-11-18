package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SurveyDistributionRepository {
    void save(SurveyDistribution distribution);
    Optional<SurveyDistribution> findById(TenantId tenantId, UUID id);
    Optional<SurveyDistribution> findActiveByTokenHash(String tokenHash);

    ReservationResult reserveResponse(TenantId tenantId, UUID distributionId,
                                      String responseId, Instant now);
    void completeResponse(TenantId tenantId, UUID distributionId, String responseId, Instant now);
    void releaseResponse(TenantId tenantId, UUID distributionId, String responseId, Instant now);
    boolean revoke(TenantId tenantId, UUID distributionId, Instant revokedAt);

    enum ReservationResult {
        RESERVED, ALREADY_RESERVED, ALREADY_COMPLETED, NOT_ACTIVE, LIMIT_REACHED
    }
}
