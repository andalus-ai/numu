package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.talent.SuccessionCandidate;
import tech.kayys.numou.workforce.domain.talent.SuccessionCandidateId;
import tech.kayys.numou.workforce.domain.talent.SuccessionPlanId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SuccessionCandidateRepository extends Repository<SuccessionCandidate, SuccessionCandidateId> {
    CompletionStage<List<SuccessionCandidate>> findByPlan(SuccessionPlanId planId);
    CompletionStage<List<SuccessionCandidate>> findByWorker(WorkerId workerId);
}
