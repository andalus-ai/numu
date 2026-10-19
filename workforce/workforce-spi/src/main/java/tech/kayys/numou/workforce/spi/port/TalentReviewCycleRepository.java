package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.TalentReviewCycle;
import tech.kayys.numou.workforce.domain.talent.TalentReviewCycleId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface TalentReviewCycleRepository extends Repository<TalentReviewCycle, TalentReviewCycleId> {
    CompletionStage<Optional<TalentReviewCycle>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<TalentReviewCycle>> findActiveByTenant(TenantId tenantId);
}
