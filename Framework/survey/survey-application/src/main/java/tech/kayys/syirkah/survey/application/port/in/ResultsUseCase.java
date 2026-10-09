package tech.kayys.syirkah.survey.application.port.in;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.domain.analytics.SurveyResults;
import tech.kayys.syirkah.survey.domain.id.SurveyId;

public interface ResultsUseCase {
    SurveyResults summary(TenantContext ctx, SurveyId id);
}
