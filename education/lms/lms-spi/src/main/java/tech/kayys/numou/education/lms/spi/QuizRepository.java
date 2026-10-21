package tech.kayys.numou.education.lms.spi;

import tech.kayys.numou.education.lms.domain.Quiz;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface QuizRepository extends Repository<Quiz, QuizId> {}
