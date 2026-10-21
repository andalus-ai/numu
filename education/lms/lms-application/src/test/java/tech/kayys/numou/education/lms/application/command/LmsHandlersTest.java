package tech.kayys.numou.education.lms.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.lms.adapter.memory.*;
import tech.kayys.numou.education.lms.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LmsHandlersTest {

    private static final Instant NOW = Instant.parse("2025-09-01T08:00:00Z");

    private final TenantId tenant = TenantId.generate();
    private final InMemoryCourseRepository courses = new InMemoryCourseRepository();
    private final InMemoryAssignmentRepository assignments = new InMemoryAssignmentRepository();
    private final InMemorySubmissionRepository submissions = new InMemorySubmissionRepository();

    private CourseId publishedCourse() {
        CourseId id = new CreateCourseHandler(courses).handle(new CreateCourseCommand(tenant, SchoolId.generate(),
                TeacherId.generate(), "IPA VII", "IPA-7", AcademicYear.of(2025), Semester.GANJIL))
                .toCompletableFuture().join().orElseThrow();
        Course c = courses.findById(id).toCompletableFuture().join().orElseThrow();
        c.addLesson("Pengantar", LessonType.READING, "doc://intro");
        c.publish();
        courses.save(c).toCompletableFuture().join();
        return id;
    }

    private AssignmentId assignment(CourseId course) {
        return new CreateAssignmentHandler(courses, assignments).handle(new CreateAssignmentCommand(tenant, course, "PR 1",
                "Kerjakan", NOW.plus(Duration.ofDays(1)), 100, false, true)).toCompletableFuture().join().orElseThrow();
    }

    private void enroll(CourseId c, StudentId s) {
        new EnrollStudentInCourseHandler(courses).handle(new EnrollStudentInCourseCommand(tenant, c, s)).toCompletableFuture().join().orElseThrow();
    }

    private SubmitAssignmentHandler submitHandler() { return new SubmitAssignmentHandler(assignments, courses, submissions); }

    @Test
    void createCourse_rejectsBlankTitle() {
        var r = new CreateCourseHandler(courses).handle(new CreateCourseCommand(tenant, SchoolId.generate(), TeacherId.generate(),
                " ", "X", AcademicYear.of(2025), Semester.GANJIL)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.course.invalid");
    }

    @Test
    void enroll_failsOnDraftCourse() {
        CourseId id = new CreateCourseHandler(courses).handle(new CreateCourseCommand(tenant, SchoolId.generate(), TeacherId.generate(),
                "IPA", "IPA-1", AcademicYear.of(2025), Semester.GANJIL)).toCompletableFuture().join().orElseThrow();
        var r = new EnrollStudentInCourseHandler(courses).handle(new EnrollStudentInCourseCommand(tenant, id, StudentId.generate())).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.course.cannot-enroll");
    }

    @Test
    void createAssignment_requiresPublishedCourse() {
        CourseId draft = new CreateCourseHandler(courses).handle(new CreateCourseCommand(tenant, SchoolId.generate(), TeacherId.generate(),
                "IPA", "IPA-1", AcademicYear.of(2025), Semester.GANJIL)).toCompletableFuture().join().orElseThrow();
        var r = new CreateAssignmentHandler(courses, assignments).handle(new CreateAssignmentCommand(tenant, draft, "PR", null,
                NOW, 100, false, true)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.course.not-published");
    }

    @Test
    void submit_requiresEnrolment() {
        CourseId c = publishedCourse();
        AssignmentId a = assignment(c);
        var r = submitHandler().handle(new SubmitAssignmentCommand(tenant, a, StudentId.generate(), "x", NOW)).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.submission.not-enrolled");
    }

    @Test
    void submit_thenResubmit_reusesSameSubmission() {
        CourseId c = publishedCourse();
        AssignmentId a = assignment(c);
        StudentId s = StudentId.generate();
        enroll(c, s);
        SubmissionId first = submitHandler().handle(new SubmitAssignmentCommand(tenant, a, s, "v1", NOW)).toCompletableFuture().join().orElseThrow();
        SubmissionId second = submitHandler().handle(new SubmitAssignmentCommand(tenant, a, s, "v2", NOW.plusSeconds(30))).toCompletableFuture().join().orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(submissions.findById(first).toCompletableFuture().join().get().getContent()).isEqualTo("v2");
    }

    @Test
    void submit_rejectedAfterDeadline() {
        CourseId c = publishedCourse();
        AssignmentId a = assignment(c);
        StudentId s = StudentId.generate();
        enroll(c, s);
        var r = submitHandler().handle(new SubmitAssignmentCommand(tenant, a, s, "late", NOW.plus(Duration.ofDays(5)))).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.submission.rejected");
    }

    @Test
    void grade_usesAssignmentMaxScore_andOnlyOnce() {
        CourseId c = publishedCourse();
        AssignmentId a = assignment(c);
        StudentId s = StudentId.generate();
        enroll(c, s);
        SubmissionId sub = submitHandler().handle(new SubmitAssignmentCommand(tenant, a, s, "x", NOW)).toCompletableFuture().join().orElseThrow();
        var grader = new GradeSubmissionHandler(submissions, assignments);

        var tooHigh = grader.handle(new GradeSubmissionCommand(tenant, sub, new BigDecimal("150"), "")).toCompletableFuture().join();
        assertThat(tooHigh.failureError().get().code()).isEqualTo("lms.submission.cannot-grade");

        assertThat(grader.handle(new GradeSubmissionCommand(tenant, sub, new BigDecimal("90"), "Bagus")).toCompletableFuture().join().isSuccess()).isTrue();
        var again = grader.handle(new GradeSubmissionCommand(tenant, sub, new BigDecimal("95"), "")).toCompletableFuture().join();
        assertThat(again.isFailure()).isTrue();
    }

    @Test
    void otherTenantCannotSeeCourse() {
        CourseId c = publishedCourse();
        var r = new EnrollStudentInCourseHandler(courses).handle(new EnrollStudentInCourseCommand(TenantId.generate(), c, StudentId.generate())).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.course.not-found");
    }
}
