package tech.kayys.numou.education.lms.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.*;
import tech.kayys.numou.education.lms.adapter.memory.*;
import tech.kayys.numou.education.lms.domain.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizHandlersTest {

    private final TenantId tenant = TenantId.generate();
    private final InMemoryCourseRepository courses = new InMemoryCourseRepository();
    private final InMemoryQuizRepository quizzes = new InMemoryQuizRepository();
    private final InMemoryQuizAttemptRepository attempts = new InMemoryQuizAttemptRepository();

    private CourseId publishedCourse(StudentId enrolled) {
        Course c = Course.create(CourseId.generate(), tenant, SchoolId.generate(), TeacherId.generate(), "IPA", "IPA-7",
                AcademicYear.of(2025), Semester.GANJIL);
        c.addLesson("Bab 1", LessonType.READING, "doc://1");
        c.publish();
        c.enroll(enrolled);
        courses.save(c).toCompletableFuture().join();
        return c.getId();
    }

    private QuizId quiz(CourseId course, int maxAttempts) {
        return new CreateQuizHandler(courses, quizzes).handle(new CreateQuizCommand(tenant, course, "Kuis", maxAttempts,
                List.of(new QuizQuestion("2+2?", List.of("3", "4"), 1, 10)), true)).toCompletableFuture().join().orElseThrow();
    }

    @Test
    void createQuiz_requiresPublishedCourse_andValidQuestions() {
        Course draft = Course.create(CourseId.generate(), tenant, SchoolId.generate(), TeacherId.generate(), "X", "X",
                AcademicYear.of(2025), Semester.GANJIL);
        courses.save(draft).toCompletableFuture().join();
        var r = new CreateQuizHandler(courses, quizzes).handle(new CreateQuizCommand(tenant, draft.getId(), "K", 1, List.of(), false))
                .toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.course.not-published");

        CourseId ok = publishedCourse(StudentId.generate());
        var empty = new CreateQuizHandler(courses, quizzes).handle(new CreateQuizCommand(tenant, ok, "K", 1, List.of(), true))
                .toCompletableFuture().join();
        assertThat(empty.failureError().get().code()).isEqualTo("lms.quiz.invalid");
    }

    @Test
    void attempt_gradesAndEnforcesLimit() {
        StudentId s = StudentId.generate();
        QuizId q = quiz(publishedCourse(s), 2);
        var handler = new SubmitQuizAttemptHandler(quizzes, courses, attempts);
        var first = handler.handle(new SubmitQuizAttemptCommand(tenant, q, s, Map.of(0, 0), Instant.now())).toCompletableFuture().join().orElseThrow();
        assertThat(attempts.findById(first).toCompletableFuture().join().get().getEarnedPoints()).isEqualTo(0);
        var second = handler.handle(new SubmitQuizAttemptCommand(tenant, q, s, Map.of(0, 1), Instant.now())).toCompletableFuture().join().orElseThrow();
        assertThat(attempts.findById(second).toCompletableFuture().join().get().getScorePercent().toPlainString()).isEqualTo("100.00");
        var third = handler.handle(new SubmitQuizAttemptCommand(tenant, q, s, Map.of(0, 1), Instant.now())).toCompletableFuture().join();
        assertThat(third.failureError().get().code()).isEqualTo("lms.quiz.attempts-exhausted");
    }

    @Test
    void attempt_requiresEnrolment() {
        QuizId q = quiz(publishedCourse(StudentId.generate()), 1);
        var r = new SubmitQuizAttemptHandler(quizzes, courses, attempts).handle(new SubmitQuizAttemptCommand(tenant, q,
                StudentId.generate(), Map.of(), Instant.now())).toCompletableFuture().join();
        assertThat(r.failureError().get().code()).isEqualTo("lms.quiz.not-enrolled");
    }
}
