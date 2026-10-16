package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycle;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PerformanceCycleRepository extends Repository<PerformanceCycle, PerformanceCycleId> {
    CompletionStage<Optional<PerformanceCycle>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<PerformanceCycle>> findActiveByTenant(TenantId tenantId);
}
