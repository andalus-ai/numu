package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.QuizAttempt;
import tech.kayys.numou.education.lms.domain.QuizAttemptId;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.education.lms.spi.QuizAttemptRepository;
import tech.kayys.numou.education.lms.spi.QuizRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Grades a quiz attempt immediately; enrolment and the attempt limit are enforced. */
public class SubmitQuizAttemptHandler {

    private final QuizRepository quizzes;
    private final CourseRepository courses;
    private final QuizAttemptRepository attempts;

    public SubmitQuizAttemptHandler(QuizRepository quizzes, CourseRepository courses, QuizAttemptRepository attempts) {
        this.quizzes = Objects.requireNonNull(quizzes);
        this.courses = Objects.requireNonNull(courses);
        this.attempts = Objects.requireNonNull(attempts);
    }

    public CompletionStage<Result<QuizAttemptId>> handle(SubmitQuizAttemptCommand cmd) {
        return quizzes.findById(cmd.quizId()).thenCompose(qOpt -> {
            if (qOpt.isEmpty() || !qOpt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<QuizAttemptId>failed("lms.quiz.not-found", "Quiz not found");
            }
            var quiz = qOpt.get();
            return courses.findById(quiz.getCourseId()).thenCompose(cOpt -> {
                if (cOpt.isEmpty() || !cOpt.get().isEnrolled(cmd.studentId())) {
                    return Outcomes.<QuizAttemptId>failed("lms.quiz.not-enrolled", "Student is not enrolled in this course");
                }
                return attempts.findByQuizAndStudent(quiz.getId(), cmd.studentId()).thenCompose(previous -> {
                    if (previous.size() >= quiz.getMaxAttempts()) {
                        return Outcomes.<QuizAttemptId>failed("lms.quiz.attempts-exhausted", "No attempts left (max " + quiz.getMaxAttempts() + ")");
                    }
                    QuizAttempt attempt;
                    try {
                        attempt = QuizAttempt.take(QuizAttemptId.generate(), cmd.tenantId(), quiz, cmd.studentId(), cmd.answers(), cmd.now());
                    } catch (IllegalStateException | NullPointerException e) {
                        return Outcomes.<QuizAttemptId>failed("lms.quiz.cannot-attempt", e.getMessage());
                    }
                    return attempts.save(attempt).thenApply(saved -> Result.success(saved.getId()));
                });
            });
        });
    }
}
