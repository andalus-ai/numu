package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.qualification.WorkerQualification;
import tech.kayys.numou.workforce.domain.qualification.WorkerQualificationId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Persistence port for the {@link WorkerQualification} aggregate root.
 */
public interface WorkerQualificationRepository extends Repository<WorkerQualification, WorkerQualificationId> {

    /**
     * Finds all qualifications held by a worker.
     */
    CompletionStage<List<WorkerQualification>> findByWorkerId(WorkerId workerId);
}
