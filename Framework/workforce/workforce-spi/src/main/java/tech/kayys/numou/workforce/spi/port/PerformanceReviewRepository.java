package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.performance.PerformanceReview;
import tech.kayys.numou.workforce.domain.performance.PerformanceReviewId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PerformanceReviewRepository extends Repository<PerformanceReview, PerformanceReviewId> {
    CompletionStage<Optional<PerformanceReview>> findByWorkerAndCycle(WorkerId workerId, PerformanceCycleId cycleId);
    CompletionStage<List<PerformanceReview>> findByReviewer(WorkerId reviewerId);
}
