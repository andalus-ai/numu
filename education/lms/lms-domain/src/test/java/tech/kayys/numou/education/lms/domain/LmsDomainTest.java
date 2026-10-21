package tech.kayys.numou.education.lms.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.lms.domain.event.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LmsDomainTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final Instant NOW = Instant.parse("2025-09-01T08:00:00Z");

    private static Course course() {
        return Course.create(CourseId.generate(), TENANT, SchoolId.generate(), TeacherId.generate(), "Matematika VII",
                "MTK-7", AcademicYear.of(2025), Semester.GANJIL);
    }

    private static Assignment publishedAssignment(boolean allowLate) {
        Assignment a = Assignment.create(AssignmentId.generate(), TENANT, CourseId.generate(), "PR 1", "Kerjakan", 
                NOW.plus(Duration.ofDays(1)), 100, allowLate);
        a.publish();
        return a;
    }

    @Test
    void course_cannotPublishWithoutLessons() {
        assertThatThrownBy(() -> course().publish()).hasMessageContaining("at least one lesson");
    }

    @Test
    void course_enrolOnlyWhenPublished_andOnlyOnce() {
        Course c = course();
        StudentId s = StudentId.generate();
        assertThatThrownBy(() -> c.enroll(s)).hasMessageContaining("not open");
        c.addLesson("Bilangan bulat", LessonType.READING, "doc://1");
        c.publish();
        c.enroll(s);
        assertThat(c.isEnrolled(s)).isTrue();
        assertThatThrownBy(() -> c.enroll(s)).hasMessageContaining("already");
    }

    @Test
    void course_lessonsArePositioned() {
        Course c = course();
        c.addLesson("A", LessonType.VIDEO, "v://1");
        Lesson second = c.addLesson("B", LessonType.LINK, "https://example.org");
        assertThat(second.position()).isEqualTo(2);
    }

    @Test
    void course_archivedCannotChange() {
        Course c = course();
        c.archive();
        assertThatThrownBy(() -> c.addLesson("A", LessonType.READING, "x")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignment_lateRules() {
        Assignment strict = publishedAssignment(false);
        Assignment lenient = publishedAssignment(true);
        Instant after = NOW.plus(Duration.ofDays(2));
        assertThat(strict.acceptsSubmissionAt(after)).isFalse();
        assertThat(lenient.acceptsSubmissionAt(after)).isTrue();
        assertThat(lenient.isLate(after)).isTrue();
    }

    @Test
    void assignment_draftDoesNotAcceptSubmissions() {
        Assignment a = Assignment.create(AssignmentId.generate(), TENANT, CourseId.generate(), "PR", null, NOW, 10, true);
        assertThat(a.acceptsSubmissionAt(NOW)).isFalse();
    }

    @Test
    void submission_marksLateWhenAllowed() {
        Assignment a = publishedAssignment(true);
        Submission s = Submission.submit(SubmissionId.generate(), TENANT, a, StudentId.generate(), "jawaban",
                NOW.plus(Duration.ofDays(3)));
        assertThat(s.isLate()).isTrue();
        assertThat(s.pullDomainEvents()).hasSize(1).first().isInstanceOf(AssignmentSubmitted.class);
    }

    @Test
    void submission_rejectedAfterDeadlineWhenStrict() {
        Assignment a = publishedAssignment(false);
        assertThatThrownBy(() -> Submission.submit(SubmissionId.generate(), TENANT, a, StudentId.generate(), "x",
                NOW.plus(Duration.ofDays(3)))).hasMessageContaining("not accepting");
    }

    @Test
    void submission_gradeValidatesRange_andFreezesWork() {
        Assignment a = publishedAssignment(false);
        Submission s = Submission.submit(SubmissionId.generate(), TENANT, a, StudentId.generate(), "x", NOW);
        assertThatThrownBy(() -> s.grade(new BigDecimal("101"), 100, "")).isInstanceOf(IllegalArgumentException.class);
        s.pullDomainEvents();
        s.grade(new BigDecimal("87.5"), 100, "Bagus");
        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.GRADED);
        assertThat(s.pullDomainEvents()).hasSize(1).first().isInstanceOf(SubmissionGraded.class);
        assertThatThrownBy(() -> s.resubmit(a, "y", NOW)).hasMessageContaining("Graded");
    }

    @Test
    void submission_returnedWorkCanBeResubmitted() {
        Assignment a = publishedAssignment(false);
        Submission s = Submission.submit(SubmissionId.generate(), TENANT, a, StudentId.generate(), "v1", NOW);
        s.returnForRevision("Perbaiki no. 3");
        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.RETURNED);
        s.resubmit(a, "v2", NOW.plusSeconds(60));
        assertThat(s.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(s.getContent()).isEqualTo("v2");
    }
}
