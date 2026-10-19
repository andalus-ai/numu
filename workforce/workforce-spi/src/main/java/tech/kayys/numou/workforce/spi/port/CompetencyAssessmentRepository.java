package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.performance.CompetencyAssessment;
import tech.kayys.numou.workforce.domain.performance.CompetencyAssessmentId;
import tech.kayys.numou.workforce.domain.performance.PerformanceCycleId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface CompetencyAssessmentRepository extends Repository<CompetencyAssessment, CompetencyAssessmentId> {
    CompletionStage<List<CompetencyAssessment>> findByWorkerAndCycle(WorkerId workerId, PerformanceCycleId cycleId);
}
