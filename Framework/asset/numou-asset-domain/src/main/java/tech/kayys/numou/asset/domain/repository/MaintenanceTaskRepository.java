package tech.kayys.syirkah.asset.domain.repository;

import tech.kayys.syirkah.asset.domain.maintenance.task.MaintenanceTask;
import tech.kayys.syirkah.asset.domain.maintenance.task.MaintenanceTaskId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface MaintenanceTaskRepository {
    CompletionStage<MaintenanceTask> save(String tenantId, MaintenanceTask task);
    CompletionStage<Optional<MaintenanceTask>> findById(String tenantId, MaintenanceTaskId id);
    CompletionStage<List<MaintenanceTask>> findByWorkOrder(String tenantId, UUID workOrderId);
}
