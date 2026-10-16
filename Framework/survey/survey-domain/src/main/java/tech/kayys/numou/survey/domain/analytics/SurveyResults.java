package tech.kayys.numou.survey.domain.analytics;

import tech.kayys.numou.survey.domain.id.SurveyId;

import java.util.List;
import java.util.Optional;

public record SurveyResults(SurveyId surveyId, int totalSubmitted, List<QuestionResult> questions, Optional<QuizStats> quiz) {}
