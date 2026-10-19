package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.qualification.WorkerQualification;
import tech.kayys.numou.workforce.domain.qualification.WorkerQualificationId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerQualificationRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryWorkerQualificationRepository implements WorkerQualificationRepository {

    private final Map<WorkerQualificationId, WorkerQualification> workerQualificationsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<WorkerQualification> save(WorkerQualification workerQualification) {
        Objects.requireNonNull(workerQualification, "workerQualification cannot be null");
        workerQualificationsById.put(workerQualification.id(), workerQualification);
        return CompletableFuture.completedFuture(workerQualification);
    }

    @Override
    public CompletionStage<Optional<WorkerQualification>> findById(WorkerQualificationId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(workerQualificationsById.get(id)));
    }

    @Override
    public CompletionStage<List<WorkerQualification>> findByWorkerId(WorkerId workerId) {
        return CompletableFuture.completedFuture(
                workerQualificationsById.values().stream()
                        .filter(wq -> wq.getWorkerId().equals(workerId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(WorkerQualificationId id) {
        return CompletableFuture.completedFuture(workerQualificationsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(WorkerQualification aggregate) {
        Objects.requireNonNull(aggregate, "workerQualification cannot be null");
        workerQualificationsById.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(WorkerQualificationId id) {
        workerQualificationsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
