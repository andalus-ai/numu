package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence port for append-only ETA estimates and versioned route plans. */
public interface CourierPlanningRepository {
    CompletionStage<CourierEtaEstimate> saveEtaEstimate(TenantId tenantId, CourierEtaEstimate estimate);
    CompletionStage<Optional<CourierEtaEstimate>> findLatestEtaEstimate(TenantId tenantId, ShipmentId shipmentId);
    CompletionStage<CourierRoutePlan> saveRoutePlan(TenantId tenantId, CourierRoutePlan routePlan);
    CompletionStage<Optional<CourierRoutePlan>> findRoutePlan(TenantId tenantId, UUID routePlanId);
}
