package tech.kayys.numou.education.lms.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.domain.Submission;
import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface SubmissionRepository extends Repository<Submission, SubmissionId> {
    CompletionStage<Optional<Submission>> findByAssignmentAndStudent(AssignmentId assignmentId, StudentId studentId);
}
