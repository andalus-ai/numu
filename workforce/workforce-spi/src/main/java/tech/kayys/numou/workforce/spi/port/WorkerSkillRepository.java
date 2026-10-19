package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.skill.WorkerSkill;
import tech.kayys.numou.workforce.domain.skill.WorkerSkillId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link WorkerSkill} aggregate root.
 */
public interface WorkerSkillRepository extends Repository<WorkerSkill, WorkerSkillId> {

    /**
     * Finds all skills held by a worker.
     */
    CompletionStage<List<WorkerSkill>> findByWorkerId(WorkerId workerId);
}
