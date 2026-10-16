package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningOffering;
import tech.kayys.numou.workforce.domain.learning.LearningOfferingId;
import tech.kayys.numou.workforce.domain.learning.LearningProgramId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LearningOfferingRepository extends Repository<LearningOffering, LearningOfferingId> {
    CompletionStage<List<LearningOffering>> findByProgram(LearningProgramId programId);
}
