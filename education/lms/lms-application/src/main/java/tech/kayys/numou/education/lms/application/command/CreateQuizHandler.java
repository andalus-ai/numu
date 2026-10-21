package tech.kayys.numou.education.lms.application.command;

import tech.kayys.numou.education.lms.domain.CourseStatus;
import tech.kayys.numou.education.lms.domain.Quiz;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.education.lms.spi.CourseRepository;
import tech.kayys.numou.education.lms.spi.QuizRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateQuizHandler {

    private final CourseRepository courses;
    private final QuizRepository quizzes;

    public CreateQuizHandler(CourseRepository courses, QuizRepository quizzes) {
        this.courses = Objects.requireNonNull(courses);
        this.quizzes = Objects.requireNonNull(quizzes);
    }

    public CompletionStage<Result<QuizId>> handle(CreateQuizCommand cmd) {
        return courses.findById(cmd.courseId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<QuizId>failed("lms.course.not-found", "Course not found");
            }
            if (opt.get().getStatus() != CourseStatus.PUBLISHED) {
                return Outcomes.<QuizId>failed("lms.course.not-published", "Quizzes require a published course");
            }
            Quiz quiz;
            try {
                quiz = Quiz.create(QuizId.generate(), cmd.tenantId(), cmd.courseId(), cmd.title(), cmd.maxAttempts());
                if (cmd.questions() != null) cmd.questions().forEach(quiz::addQuestion);
                if (cmd.publish()) quiz.publish();
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
                return Outcomes.<QuizId>failed("lms.quiz.invalid", e.getMessage());
            }
            return quizzes.save(quiz).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
