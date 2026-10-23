package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped persistence port for shipment booking aggregates. */
public interface ShipmentRepository {
    CompletionStage<Shipment> save(TenantId tenantId, Shipment shipment);
    CompletionStage<Optional<Shipment>> findById(TenantId tenantId, ShipmentId shipmentId);
    CompletionStage<Optional<Shipment>> findByReference(TenantId tenantId, String reference);
    CompletionStage<Void> deleteDraft(TenantId tenantId, ShipmentId shipmentId);
}