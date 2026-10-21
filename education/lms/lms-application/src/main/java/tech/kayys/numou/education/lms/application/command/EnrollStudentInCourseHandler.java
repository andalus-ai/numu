package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class EnrollStudentInCourseHandler {

    private final CourseRepository courses;

    public EnrollStudentInCourseHandler(CourseRepository courses) {
        this.courses = Objects.requireNonNull(courses);
    }

    public CompletionStage<Result<CourseId>> handle(EnrollStudentInCourseCommand cmd) {
        return courses.findById(cmd.courseId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<CourseId>failed("lms.course.not-found", "Course not found");
            }
            var course = opt.get();
            try {
                course.enroll(cmd.studentId());
            } catch (IllegalStateException e) {
                return Outcomes.<CourseId>failed("lms.course.cannot-enroll", e.getMessage());
            }
            return courses.save(course).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
