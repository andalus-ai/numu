package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.talent.SuccessionCandidateId;
import tech.kayys.numou.workforce.domain.talent.SuccessionReadinessAssessment;
import tech.kayys.numou.workforce.domain.talent.SuccessionReadinessAssessmentId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface SuccessionReadinessAssessmentRepository extends Repository<SuccessionReadinessAssessment, SuccessionReadinessAssessmentId> {
    CompletionStage<List<SuccessionReadinessAssessment>> findByCandidate(SuccessionCandidateId candidateId);
}
