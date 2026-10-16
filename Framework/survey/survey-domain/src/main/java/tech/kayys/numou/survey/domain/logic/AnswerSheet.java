package tech.kayys.numou.survey.domain.logic;

import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;

import java.util.Optional;

/** Read-only view of the answers given so far (empty answers are reported as absent). */
@FunctionalInterface
public interface AnswerSheet {
    Optional<AnswerValue> get(QuestionId id);
}
