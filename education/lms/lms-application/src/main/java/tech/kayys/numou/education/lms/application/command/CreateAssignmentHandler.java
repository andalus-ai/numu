package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.Assignment;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.education.lms.domain.CourseStatus;
import tech.kayys.numou.education.lms.spi.AssignmentRepository;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateAssignmentHandler {

    private final CourseRepository courses;
    private final AssignmentRepository assignments;

    public CreateAssignmentHandler(CourseRepository courses, AssignmentRepository assignments) {
        this.courses = Objects.requireNonNull(courses);
        this.assignments = Objects.requireNonNull(assignments);
    }

    public CompletionStage<Result<AssignmentId>> handle(CreateAssignmentCommand cmd) {
        return courses.findById(cmd.courseId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<AssignmentId>failed("lms.course.not-found", "Course not found");
            }
            if (opt.get().getStatus() != CourseStatus.PUBLISHED) {
                return Outcomes.<AssignmentId>failed("lms.course.not-published", "Assignments require a published course");
            }
            Assignment a;
            try {
                a = Assignment.create(AssignmentId.generate(), cmd.tenantId(), cmd.courseId(), cmd.title(),
                        cmd.instructions(), cmd.dueAt(), cmd.maxScore(), cmd.allowLate());
                if (cmd.publish()) a.publish();
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<AssignmentId>failed("lms.assignment.invalid", e.getMessage());
            }
            return assignments.save(a).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
