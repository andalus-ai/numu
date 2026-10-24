package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence port for courier assignment and delivery outcomes. */
public interface DeliveryTaskRepository {
    CompletionStage<DeliveryTask> save(TenantId tenantId, DeliveryTask task);
    CompletionStage<Optional<DeliveryTask>> findById(TenantId tenantId, DeliveryTaskId taskId);
    CompletionStage<List<DeliveryTask>> findByCourier(TenantId tenantId, String courierReference, int limit);
    CompletionStage<Optional<DeliveryTask>> findActiveByShipment(TenantId tenantId, ShipmentId shipmentId);
}
