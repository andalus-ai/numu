package tech.kayys.numou.education.lms.spi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.QuizAttempt;
import tech.kayys.numou.education.lms.domain.QuizAttemptId;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface QuizAttemptRepository extends Repository<QuizAttempt, QuizAttemptId> {
    CompletionStage<List<QuizAttempt>> findByQuizAndStudent(QuizId quizId, StudentId studentId);
}
