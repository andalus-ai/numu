package tech.kayys.numou.survey.domain.quiz;

import tech.kayys.numou.survey.domain.id.QuestionId;

public record QuestionScore(QuestionId question, double earned, double max, boolean fullyCorrect) {}
