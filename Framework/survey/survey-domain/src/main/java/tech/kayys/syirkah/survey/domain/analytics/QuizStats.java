package tech.kayys.syirkah.survey.domain.analytics;

import tech.kayys.syirkah.survey.domain.id.QuestionId;

import java.util.Map;

public record QuizStats(int attempts, double averagePercent, double passRate, double highestPercent,
                        double lowestPercent, Map<QuestionId, Double> correctRate) {}
