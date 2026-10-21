package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.Course;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateCourseHandler {

    private final CourseRepository courses;

    public CreateCourseHandler(CourseRepository courses) {
        this.courses = Objects.requireNonNull(courses);
    }

    public CompletionStage<Result<CourseId>> handle(CreateCourseCommand cmd) {
        Course course;
        try {
            course = Course.create(CourseId.generate(), cmd.tenantId(), cmd.schoolId(), cmd.instructor(), cmd.title(),
                    cmd.code(), cmd.academicYear(), cmd.semester());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Outcomes.failed("lms.course.invalid", e.getMessage());
        }
        return courses.save(course).thenApply(saved -> Result.success(saved.getId()));
    }
}
