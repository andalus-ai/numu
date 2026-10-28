package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.foundation.domain.ref.PersonRef;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.Worker;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryWorkerRepository implements WorkerRepository {

    private final Map<WorkerId, Worker> workersById = new ConcurrentHashMap<>();
    private final Map<TenantPersonKey, WorkerId> idsByPerson = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Worker> save(Worker worker) {
        Objects.requireNonNull(worker, "worker cannot be null");
        workersById.put(worker.id(), worker);
        idsByPerson.put(new TenantPersonKey(worker.tenantId(), worker.person()), worker.id());
        return CompletableFuture.completedFuture(worker);
    }

    @Override
    public CompletionStage<Optional<Worker>> findById(WorkerId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(workersById.get(id)));
    }

    @Override
    public CompletionStage<Optional<Worker>> findByPersonRef(TenantId tenantId, PersonRef personRef) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(personRef, "personRef cannot be null");
        var id = idsByPerson.get(new TenantPersonKey(tenantId, personRef));
        return CompletableFuture.completedFuture(
                id == null ? Optional.empty() : Optional.ofNullable(workersById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(WorkerId id) {
        return CompletableFuture.completedFuture(workersById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Worker worker) {
        Objects.requireNonNull(worker, "worker cannot be null");
        workersById.remove(worker.id());
        idsByPerson.remove(new TenantPersonKey(worker.tenantId(), worker.person()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(WorkerId id) {
        var removed = workersById.remove(id);
        if (removed != null) {
            idsByPerson.remove(new TenantPersonKey(removed.tenantId(), removed.person()));
        }
        return CompletableFuture.completedFuture(null);
    }
    private record TenantPersonKey(TenantId tenantId, PersonRef personRef) { }
}
