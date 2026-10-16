package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.maintenance.workorder.MaintenanceWorkOrder;
import tech.kayys.syirkah.asset.domain.maintenance.workorder.MaintenanceWorkOrderId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface MaintenanceWorkOrderRepository {
    CompletionStage<MaintenanceWorkOrder> save(String tenantId, MaintenanceWorkOrder workOrder);
    CompletionStage<Optional<MaintenanceWorkOrder>> findById(String tenantId, MaintenanceWorkOrderId id);
    CompletionStage<List<MaintenanceWorkOrder>> findByAsset(String tenantId, UUID assetId);
    CompletionStage<Boolean> existsByNumber(String tenantId, String workOrderNumber);
}
