package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.foundation.domain.ref.PersonRef;
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
    private final Map<PersonRef, WorkerId> idsByPerson = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Worker> save(Worker worker) {
        Objects.requireNonNull(worker, "worker cannot be null");
        workersById.put(worker.id(), worker);
        idsByPerson.put(worker.person(), worker.id());
        return CompletableFuture.completedFuture(worker);
    }

    @Override
    public CompletionStage<Optional<Worker>> findById(WorkerId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(workersById.get(id)));
    }

    @Override
    public CompletionStage<Optional<Worker>> findByPersonRef(PersonRef personRef) {
        var id = idsByPerson.get(personRef);
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
        idsByPerson.remove(worker.person());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(WorkerId id) {
        var removed = workersById.remove(id);
        if (removed != null) {
            idsByPerson.remove(removed.person());
        }
        return CompletableFuture.completedFuture(null);
    }
}
