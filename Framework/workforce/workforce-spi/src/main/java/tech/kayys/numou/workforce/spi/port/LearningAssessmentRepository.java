package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningAssessment;
import tech.kayys.numou.workforce.domain.learning.LearningAssessmentId;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LearningAssessmentRepository extends Repository<LearningAssessment, LearningAssessmentId> {
    CompletionStage<List<LearningAssessment>> findByEnrollment(LearningEnrollmentId enrollmentId);
}
