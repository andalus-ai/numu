package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.performance.PerformanceFeedback;
import tech.kayys.numou.workforce.domain.performance.PerformanceFeedbackId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PerformanceFeedbackRepository extends Repository<PerformanceFeedback, PerformanceFeedbackId> {
    CompletionStage<List<PerformanceFeedback>> findByWorker(WorkerId workerId);
}
