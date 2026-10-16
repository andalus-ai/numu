package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.development.WorkerCareerPlan;
import tech.kayys.numou.workforce.domain.development.WorkerCareerPlanId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface WorkerCareerPlanRepository extends Repository<WorkerCareerPlan, WorkerCareerPlanId> {
    CompletionStage<Optional<WorkerCareerPlan>> findActiveByWorker(WorkerId workerId);
}
