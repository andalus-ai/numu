package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoal;
import tech.kayys.numou.workforce.domain.performance.PerformanceGoalId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PerformanceGoalRepository extends Repository<PerformanceGoal, PerformanceGoalId> {
    CompletionStage<List<PerformanceGoal>> findByWorkerAndCycle(WorkerId workerId, PerformanceCycleId cycleId);
}
