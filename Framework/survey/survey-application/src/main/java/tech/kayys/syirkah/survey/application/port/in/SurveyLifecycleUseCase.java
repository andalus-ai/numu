package tech.kayys.syirkah.survey.application.port.in;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.survey.domain.survey.Survey;

import java.util.List;

public interface SurveyLifecycleUseCase {
    /** Dry-run of publish: what is still wrong. Empty list = ready. */
    List<String> checkReadiness(TenantContext ctx, SurveyId id);

    Survey publish(TenantContext ctx, SurveyId id);

    Survey close(TenantContext ctx, SurveyId id);

    Survey archive(TenantContext ctx, SurveyId id);

    /** Creates the next DRAFT version of a published/closed survey and returns it. */
    Survey revise(TenantContext ctx, SurveyId id);
}
