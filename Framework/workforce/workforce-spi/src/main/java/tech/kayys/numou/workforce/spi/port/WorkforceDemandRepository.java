package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.WorkforceDemand;
import tech.kayys.numou.workforce.domain.analytics.WorkforceDemandId;
import tech.kayys.numou.workforce.domain.position.PositionId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface WorkforceDemandRepository extends Repository<WorkforceDemand, WorkforceDemandId> {
    CompletionStage<List<WorkforceDemand>> findByPosition(PositionId positionId);
    CompletionStage<List<WorkforceDemand>> findActiveByTenant(TenantId tenantId);
}
