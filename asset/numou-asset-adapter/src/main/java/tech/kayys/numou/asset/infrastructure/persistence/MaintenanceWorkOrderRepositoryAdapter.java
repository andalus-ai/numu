package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrder;
import tech.kayys.numou.asset.domain.maintenance.workorder.MaintenanceWorkOrderId;
import tech.kayys.numou.asset.domain.repository.MaintenanceWorkOrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache adapter for maintenance work orders (ASSET-19). Explicit sessions. */
@ApplicationScoped
public class MaintenanceWorkOrderRepositoryAdapter implements MaintenanceWorkOrderRepository {

    @Override
    public CompletionStage<MaintenanceWorkOrder> save(String tenantId, MaintenanceWorkOrder workOrder) {
        MaintenanceWorkOrderEntity entity = toEntity(tenantId, workOrder);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<MaintenanceWorkOrderEntity>merge(entity))
                        .replaceWith(workOrder))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<MaintenanceWorkOrder>> findById(
            String tenantId, MaintenanceWorkOrderId id) {
        return Panache.withSession(() -> MaintenanceWorkOrderEntity
                        .<MaintenanceWorkOrderEntity>find("tenantId = ?1 and id = ?2",
                                tenantId, id.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity)
                                .map(MaintenanceWorkOrderRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<MaintenanceWorkOrder>> findByAsset(String tenantId, UUID assetId) {
        return Panache.withSession(() -> MaintenanceWorkOrderEntity
                        .<MaintenanceWorkOrderEntity>list(
                                "tenantId = ?1 and assetId = ?2 order by createdAt desc, id desc",
                                tenantId, assetId)
                        .map(rows -> rows.stream()
                                .map(MaintenanceWorkOrderRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Boolean> existsByNumber(String tenantId, String workOrderNumber) {
        return Panache.withSession(() -> MaintenanceWorkOrderEntity.count(
                        "tenantId = ?1 and workOrderNumber = ?2", tenantId, workOrderNumber)
                        .map(count -> count > 0))
                .subscribe().asCompletionStage();
    }

    private static MaintenanceWorkOrderEntity toEntity(
            String tenantId, MaintenanceWorkOrder wo) {
        MaintenanceWorkOrderEntity e = new MaintenanceWorkOrderEntity();
        e.id = wo.id().value();
        e.tenantId = tenantId;
        e.assetId = wo.assetId();
        e.workOrderNumber = wo.workOrderNumber();
        e.title = wo.title();
        e.description = wo.description();
        e.workOrderType = wo.type();
        e.priority = wo.priority();
        e.status = wo.status();
        e.requestedBy = wo.requestedBy();
        e.assignedTo = wo.assignedTo();
        e.openedAt = wo.openedAt();
        e.startedAt = wo.startedAt();
        e.completedAt = wo.completedAt();
        e.cancelledAt = wo.cancelledAt();
        e.createdAt = wo.getCreatedAt();
        e.updatedAt = wo.getUpdatedAt();
        return e;
    }

    private static MaintenanceWorkOrder toDomain(MaintenanceWorkOrderEntity e) {
        MaintenanceWorkOrder wo = MaintenanceWorkOrder.reconstitute(
                MaintenanceWorkOrderId.of(e.id), e.tenantId, e.assetId,
                e.workOrderNumber, e.title, e.description, e.workOrderType, e.priority,
                e.status, e.requestedBy, e.assignedTo, e.openedAt, e.startedAt,
                e.completedAt, e.cancelledAt);
        if (e.createdAt != null) {
            wo.setCreatedAt(e.createdAt);
        }
        if (e.updatedAt != null) {
            wo.setUpdatedAt(e.updatedAt);
        }
        if (e.version != null) {
            wo.setVersion(e.version.intValue());
        }
        return wo;
    }
}