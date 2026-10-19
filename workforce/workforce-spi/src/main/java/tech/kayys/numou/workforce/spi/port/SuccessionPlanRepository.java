package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.CriticalPositionId;
import tech.kayys.numou.workforce.domain.talent.SuccessionPlan;
import tech.kayys.numou.workforce.domain.talent.SuccessionPlanId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface SuccessionPlanRepository extends Repository<SuccessionPlan, SuccessionPlanId> {
    CompletionStage<Optional<SuccessionPlan>> findByCriticalPosition(CriticalPositionId criticalPositionId);
    CompletionStage<List<SuccessionPlan>> findActiveByTenant(TenantId tenantId);
}
