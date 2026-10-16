package tech.kayys.numou.survey.application.port.in;

import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.domain.analytics.SurveyResults;
import tech.kayys.numou.survey.domain.id.SurveyId;

public interface ResultsUseCase {
    SurveyResults summary(TenantContext ctx, SurveyId id);
}
