package tech.kayys.numou.feedback.application.command;

import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RevokeSurveyDistributionHandler {
    private final SurveyDistributionRepository repository;

    public RevokeSurveyDistributionHandler(SurveyDistributionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public boolean handle(TenantId tenantId, UUID distributionId, Instant revokedAt) {
        return repository.revoke(tenantId, distributionId, revokedAt);
    }
}
