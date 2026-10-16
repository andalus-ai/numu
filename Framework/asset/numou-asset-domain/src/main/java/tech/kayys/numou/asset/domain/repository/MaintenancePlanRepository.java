package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.maintenance.plan.MaintenancePlan;
import tech.kayys.syirkah.asset.domain.maintenance.plan.MaintenancePlanId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped outbound port for maintenance plans (ASSET-22). */
public interface MaintenancePlanRepository {
    CompletionStage<Optional<MaintenancePlan>> findByTenantAndId(String tenantId, MaintenancePlanId id);
    CompletionStage<Boolean> existsByTenantAndPlanNumber(String tenantId, String planNumber);
    CompletionStage<Void> save(MaintenancePlan plan);
}
