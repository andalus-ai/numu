package tech.kayys.numou.survey.application.service;

import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResultsUseCase;
import tech.kayys.numou.survey.application.port.out.ResponseRepository;
import tech.kayys.numou.survey.application.port.out.SurveyRepository;
import tech.kayys.numou.survey.application.port.out.TenantRepository;
import tech.kayys.numou.survey.domain.analytics.SurveyResults;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.service.ResultsAggregator;
import tech.kayys.numou.survey.domain.survey.Survey;

public final class ResultsService implements ResultsUseCase {
    private final ResponseRepository responses;
    private final TenantGate gate;
    private final ResultsAggregator aggregator = new ResultsAggregator();

    public ResultsService(SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants) {
        this.responses = responses;
        this.gate = new TenantGate(tenants, surveys);
    }

    @Override
    public SurveyResults summary(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        Survey survey = gate.survey(ctx.tenantId(), id);
        return aggregator.aggregate(survey, responses.findSubmitted(ctx.tenantId(), id));
    }
}
