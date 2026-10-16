package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.WorkforcePlan;
import tech.kayys.numou.workforce.domain.analytics.WorkforcePlanId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface WorkforcePlanRepository extends Repository<WorkforcePlan, WorkforcePlanId> {
    CompletionStage<Optional<WorkforcePlan>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<WorkforcePlan>> findByTenant(TenantId tenantId);
}
