package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.talent.CriticalPosition;
import tech.kayys.numou.workforce.domain.talent.CriticalPositionId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CriticalPositionRepository extends Repository<CriticalPosition, CriticalPositionId> {
    CompletionStage<Optional<CriticalPosition>> findByPosition(PositionId positionId);
    CompletionStage<List<CriticalPosition>> findActiveByTenant(TenantId tenantId);
}
