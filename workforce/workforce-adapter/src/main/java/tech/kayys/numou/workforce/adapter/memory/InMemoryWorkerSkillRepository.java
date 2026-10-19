package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.skill.WorkerSkill;
import tech.kayys.numou.workforce.domain.skill.WorkerSkillId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.WorkerSkillRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryWorkerSkillRepository implements WorkerSkillRepository {

    private final Map<WorkerSkillId, WorkerSkill> workerSkillsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<WorkerSkill> save(WorkerSkill workerSkill) {
        Objects.requireNonNull(workerSkill, "workerSkill cannot be null");
        workerSkillsById.put(workerSkill.id(), workerSkill);
        return CompletableFuture.completedFuture(workerSkill);
    }

    @Override
    public CompletionStage<Optional<WorkerSkill>> findById(WorkerSkillId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(workerSkillsById.get(id)));
    }

    @Override
    public CompletionStage<List<WorkerSkill>> findByWorkerId(WorkerId workerId) {
        return CompletableFuture.completedFuture(
                workerSkillsById.values().stream()
                        .filter(ws -> ws.getWorkerId().equals(workerId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(WorkerSkillId id) {
        return CompletableFuture.completedFuture(workerSkillsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(WorkerSkill aggregate) {
        Objects.requireNonNull(aggregate, "workerSkill cannot be null");
        workerSkillsById.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(WorkerSkillId id) {
        workerSkillsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
