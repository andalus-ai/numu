package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.Submission;
import tech.kayys.numou.education.lms.domain.SubmissionId;
import tech.kayys.numou.education.lms.spi.AssignmentRepository;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.education.lms.spi.SubmissionRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Creates a submission, or replaces the student's earlier one while it is still revisable. */
public class SubmitAssignmentHandler {

    private final AssignmentRepository assignments;
    private final CourseRepository courses;
    private final SubmissionRepository submissions;

    public SubmitAssignmentHandler(AssignmentRepository assignments, CourseRepository courses,
                                   SubmissionRepository submissions) {
        this.assignments = Objects.requireNonNull(assignments);
        this.courses = Objects.requireNonNull(courses);
        this.submissions = Objects.requireNonNull(submissions);
    }

    public CompletionStage<Result<SubmissionId>> handle(SubmitAssignmentCommand cmd) {
        return assignments.findById(cmd.assignmentId()).thenCompose(aOpt -> {
            if (aOpt.isEmpty() || !aOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<SubmissionId>failed("lms.assignment.not-found", "Assignment not found");
            }
            var assignment = aOpt.get();
            return courses.findById(assignment.getCourseId()).thenCompose(cOpt -> {
                if (cOpt.isEmpty() || !cOpt.get().isEnrolled(cmd.studentId())) {
                    return Outcomes.<SubmissionId>failed("lms.submission.not-enrolled", "Student is not enrolled in this course");
                }
                return submissions.findByAssignmentAndStudent(assignment.getId(), cmd.studentId()).thenCompose(existing -> {
                    Submission submission;
                    try {
                        if (existing.isPresent()) {
                            submission = existing.get();
                            submission.resubmit(assignment, cmd.content(), cmd.now());
                        } else {
                            submission = Submission.submit(SubmissionId.generate(), cmd.tenantId(), assignment,
                                    cmd.studentId(), cmd.content(), cmd.now());
                        }
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        return Outcomes.<SubmissionId>failed("lms.submission.rejected", e.getMessage());
                    }
                    return submissions.save(submission).thenApply(saved -> Result.success(saved.getId()));
                });
            });
        });
    }
}
