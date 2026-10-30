package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * In-memory test/development adapter. Mutations are synchronized so the primary
 * aggregate map and the tenant/person uniqueness index change as one operation.
 */
public final class InMemoryWorkerRepository implements WorkerRepository {

    private final Map<WorkerId, Worker> workersById = new HashMap<>();
    private final Map<TenantPersonKey, WorkerId> idsByPerson = new HashMap<>();

    @Override
    public synchronized CompletionStage<Worker> save(Worker worker) {
        Objects.requireNonNull(worker, "worker cannot be null");

        var newKey = new TenantPersonKey(worker.tenantId(), worker.person());
        var indexedWorkerId = idsByPerson.get(newKey);
        if (indexedWorkerId != null && !indexedWorkerId.equals(worker.id())) {
            throw new IllegalStateException("A worker already exists for this tenant and person");
        }

        var previous = workersById.get(worker.id());
        if (previous != null) {
            var previousKey = new TenantPersonKey(previous.tenantId(), previous.person());
            if (!previousKey.equals(newKey)) {
                idsByPerson.remove(previousKey, worker.id());
            }
        }

        workersById.put(worker.id(), worker);
        idsByPerson.put(newKey, worker.id());
        return CompletableFuture.completedFuture(worker);
    }

    @Override
    public synchronized CompletionStage<Optional<Worker>> findById(WorkerId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(Optional.ofNullable(workersById.get(id)));
    }

    @Override
    public synchronized CompletionStage<Optional<Worker>> findById(TenantId tenantId, WorkerId workerId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(workerId, "workerId cannot be null");
        var worker = workersById.get(workerId);
        return CompletableFuture.completedFuture(
                worker != null && worker.tenantId().equals(tenantId)
                        ? Optional.of(worker)
                        : Optional.empty()
        );
    }

    @Override
    public synchronized CompletionStage<Optional<Worker>> findByPersonRef(TenantId tenantId, PersonRef personRef) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(personRef, "personRef cannot be null");
        var id = idsByPerson.get(new TenantPersonKey(tenantId, personRef));
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(workersById.get(id))
        );
    }

    @Override
    public synchronized CompletionStage<Boolean> existsById(WorkerId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(workersById.containsKey(id));
    }

    @Override
    public synchronized CompletionStage<Boolean> existsById(TenantId tenantId, WorkerId workerId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(workerId, "workerId cannot be null");
        var worker = workersById.get(workerId);
        return CompletableFuture.completedFuture(worker != null && worker.tenantId().equals(tenantId));
    }

    @Override
    public synchronized CompletionStage<Void> delete(Worker worker) {
        Objects.requireNonNull(worker, "worker cannot be null");
        return deleteById(worker.tenantId(), worker.id());
    }

    @Override
    public synchronized CompletionStage<Void> deleteById(WorkerId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return deleteStoredWorker(id);
    }

    @Override
    public synchronized CompletionStage<Void> deleteById(TenantId tenantId, WorkerId workerId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(workerId, "workerId cannot be null");
        var worker = workersById.get(workerId);
        if (worker != null && worker.tenantId().equals(tenantId)) {
            return deleteStoredWorker(workerId);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> deleteStoredWorker(WorkerId id) {
        var removed = workersById.remove(id);
        if (removed != null) {
            idsByPerson.remove(new TenantPersonKey(removed.tenantId(), removed.person()), id);
        }
        return CompletableFuture.completedFuture(null);
    }

    private record TenantPersonKey(TenantId tenantId, PersonRef personRef) { }
}
