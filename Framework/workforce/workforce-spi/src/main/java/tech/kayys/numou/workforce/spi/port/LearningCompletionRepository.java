package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningCompletion;
import tech.kayys.numou.workforce.domain.learning.LearningCompletionId;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface LearningCompletionRepository extends Repository<LearningCompletion, LearningCompletionId> {
    CompletionStage<Optional<LearningCompletion>> findByEnrollment(LearningEnrollmentId enrollmentId);
}
