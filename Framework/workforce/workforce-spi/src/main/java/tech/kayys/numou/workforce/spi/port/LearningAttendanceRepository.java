package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.learning.LearningAttendance;
import tech.kayys.numou.workforce.domain.learning.LearningAttendanceId;
import tech.kayys.numou.workforce.domain.learning.LearningEnrollmentId;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface LearningAttendanceRepository extends Repository<LearningAttendance, LearningAttendanceId> {
    CompletionStage<List<LearningAttendance>> findByEnrollment(LearningEnrollmentId enrollmentId);
}
