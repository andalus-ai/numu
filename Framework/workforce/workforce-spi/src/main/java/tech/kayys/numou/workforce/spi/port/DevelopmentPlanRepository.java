package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.development.DevelopmentPlan;
import tech.kayys.numou.workforce.domain.development.DevelopmentPlanId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface DevelopmentPlanRepository extends Repository<DevelopmentPlan, DevelopmentPlanId> {
    CompletionStage<Optional<DevelopmentPlan>> findByWorkerAndYear(WorkerId workerId, int cycleYear);
    CompletionStage<List<DevelopmentPlan>> findByWorker(WorkerId workerId);
}
