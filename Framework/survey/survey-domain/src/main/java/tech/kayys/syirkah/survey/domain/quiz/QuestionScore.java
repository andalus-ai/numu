package tech.kayys.syirkah.survey.domain.quiz;

import tech.kayys.syirkah.survey.domain.id.QuestionId;

public record QuestionScore(QuestionId question, double earned, double max, boolean fullyCorrect) {}
