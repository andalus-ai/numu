package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollment;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;
import tech.kayys.numou.workforce.domain.learning.LearningOfferingId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface LearningEnrollmentRepository extends Repository<LearningEnrollment, LearningEnrollmentId> {
    CompletionStage<Optional<LearningEnrollment>> findByWorkerAndOffering(WorkerId workerId, LearningOfferingId offeringId);
    CompletionStage<List<LearningEnrollment>> findByWorker(WorkerId workerId);
    CompletionStage<List<LearningEnrollment>> findByOffering(LearningOfferingId offeringId);
}
