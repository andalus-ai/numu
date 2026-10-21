package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.education.lms.spi.AssignmentRepository;
import tech.kayys.numou.education.lms.spi.SubmissionRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class GradeSubmissionHandler {

    private final SubmissionRepository submissions;
    private final AssignmentRepository assignments;

    public GradeSubmissionHandler(SubmissionRepository submissions, AssignmentRepository assignments) {
        this.submissions = Objects.requireNonNull(submissions);
        this.assignments = Objects.requireNonNull(assignments);
    }

    public CompletionStage<Result<SubmissionId>> handle(GradeSubmissionCommand cmd) {
        return submissions.findById(cmd.submissionId()).thenCompose(sOpt -> {
            if (sOpt.isEmpty() || !sOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<SubmissionId>failed("lms.submission.not-found", "Submission not found");
            }
            var submission = sOpt.get();
            return assignments.findById(submission.getAssignmentId()).thenCompose(aOpt -> {
                if (aOpt.isEmpty()) return Outcomes.<SubmissionId>failed("lms.assignment.not-found", "Assignment not found");
                try {
                    submission.grade(cmd.score(), aOpt.get().getMaxScore(), cmd.feedback());
                } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
                    return Outcomes.<SubmissionId>failed("lms.submission.cannot-grade", e.getMessage());
                }
                return submissions.save(submission).thenApply(saved -> Result.success(saved.getId()));
            });
        });
    }
}
