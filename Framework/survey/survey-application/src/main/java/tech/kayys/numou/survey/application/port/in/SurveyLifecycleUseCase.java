package tech.kayys.numou.survey.application.port.in;

import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.survey.Survey;

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
