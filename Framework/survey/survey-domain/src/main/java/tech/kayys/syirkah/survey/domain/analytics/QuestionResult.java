package tech.kayys.syirkah.survey.domain.analytics;

import tech.kayys.syirkah.survey.domain.id.QuestionId;

import java.util.Map;

/** Aggregated answers for one question; the shape of {@code stats} depends on the question kind. */
public record QuestionResult(QuestionId question, String title, String kind, long answered, Map<String, Object> stats) {}
