package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningCertificate;
import tech.kayys.numou.workforce.domain.learning.LearningCertificateId;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface LearningCertificateRepository extends Repository<LearningCertificate, LearningCertificateId> {
    CompletionStage<Optional<LearningCertificate>> findByEnrollment(LearningEnrollmentId enrollmentId);
}
