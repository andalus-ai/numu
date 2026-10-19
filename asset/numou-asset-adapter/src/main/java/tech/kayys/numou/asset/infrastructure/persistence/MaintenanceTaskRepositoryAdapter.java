package tech.kayys.numou.asset.infrastructure.persistence;

import io.quarkus.hibernate.reactive.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.asset.domain.maintenance.task.MaintenanceTask;
import tech.kayys.numou.asset.domain.maintenance.task.MaintenanceTaskId;
import tech.kayys.numou.asset.domain.repository.MaintenanceTaskRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/** Panache adapter for maintenance tasks (ASSET-19). Explicit sessions. */
@ApplicationScoped
public class MaintenanceTaskRepositoryAdapter implements MaintenanceTaskRepository {

    @Override
    public CompletionStage<MaintenanceTask> save(String tenantId, MaintenanceTask task) {
        MaintenanceTaskEntity entity = toEntity(tenantId, task);
        return Panache.withTransaction(() -> Panache.getSession()
                        .flatMap(session -> session.<MaintenanceTaskEntity>merge(entity))
                        .replaceWith(task))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<Optional<MaintenanceTask>> findById(String tenantId, MaintenanceTaskId id) {
        return Panache.withSession(() -> MaintenanceTaskEntity
                        .<MaintenanceTaskEntity>find("tenantId = ?1 and id = ?2", tenantId, id.value())
                        .firstResult()
                        .map(entity -> Optional.ofNullable(entity)
                                .map(MaintenanceTaskRepositoryAdapter::toDomain)))
                .subscribe().asCompletionStage();
    }

    @Override
    public CompletionStage<List<MaintenanceTask>> findByWorkOrder(String tenantId, UUID workOrderId) {
        return Panache.withSession(() -> MaintenanceTaskEntity
                        .<MaintenanceTaskEntity>list(
                                "tenantId = ?1 and workOrderId = ?2 order by sequence asc, id asc",
                                tenantId, workOrderId)
                        .map(rows -> rows.stream()
                                .map(MaintenanceTaskRepositoryAdapter::toDomain)
                                .collect(Collectors.toList())))
                .subscribe().asCompletionStage();
    }

    private static MaintenanceTaskEntity toEntity(String tenantId, MaintenanceTask t) {
        MaintenanceTaskEntity e = new MaintenanceTaskEntity();
        e.id = t.id().value();
        e.tenantId = tenantId;
        e.workOrderId = t.workOrderId();
        e.taskNumber = t.taskNumber();
        e.title = t.title();
        e.description = t.description();
        e.sequence = t.sequence();
        e.status = t.status();
        e.completedAt = t.completedAt();
        e.createdAt = t.createdAt() == null ? Instant.now() : t.createdAt();
        return e;
    }

    private static MaintenanceTask toDomain(MaintenanceTaskEntity e) {
        return new MaintenanceTask(
                MaintenanceTaskId.of(e.id), e.tenantId, e.workOrderId,
                e.taskNumber, e.title, e.description, e.sequence,
                e.status, e.createdAt, e.completedAt);
    }
}