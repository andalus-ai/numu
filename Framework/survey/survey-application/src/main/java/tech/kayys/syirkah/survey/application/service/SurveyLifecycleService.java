package tech.kayys.syirkah.survey.application.service;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.application.port.in.SurveyLifecycleUseCase;
import tech.kayys.syirkah.survey.application.port.out.EventPublisher;
import tech.kayys.syirkah.survey.application.port.out.IdGenerator;
import tech.kayys.syirkah.survey.application.port.out.SurveyRepository;
import tech.kayys.syirkah.survey.application.port.out.TenantRepository;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.survey.domain.survey.Survey;
import tech.kayys.syirkah.survey.domain.survey.SurveyStatus;
import tech.kayys.syirkah.survey.domain.tenant.Tenant;

import java.time.Clock;
import java.util.List;

public final class SurveyLifecycleService implements SurveyLifecycleUseCase {
    private final SurveyRepository surveys;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;
    private final TenantGate gate;

    public SurveyLifecycleService(SurveyRepository surveys, TenantRepository tenants, EventPublisher events,
                                  IdGenerator ids, Clock clock) {
        this.surveys = surveys;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.gate = new TenantGate(tenants, surveys);
    }

    @Override
    public List<String> checkReadiness(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        return gate.survey(ctx.tenantId(), id).publishProblems();
    }

    @Override
    public Survey publish(TenantContext ctx, SurveyId id) {
        Tenant tenant = gate.activeTenant(ctx.tenantId());
        Survey s = gate.survey(ctx.tenantId(), id);
        tenant.ensurePublishQuota(surveys.countByStatus(ctx.tenantId(), SurveyStatus.PUBLISHED));
        s.publish(clock.instant());
        return persist(s);
    }

    @Override
    public Survey close(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        Survey s = gate.survey(ctx.tenantId(), id);
        s.close(clock.instant());
        return persist(s);
    }

    @Override
    public Survey archive(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        Survey s = gate.survey(ctx.tenantId(), id);
        s.archive(clock.instant());
        return persist(s);
    }

    @Override
    public Survey revise(TenantContext ctx, SurveyId id) {
        gate.activeTenant(ctx.tenantId());
        Survey current = gate.survey(ctx.tenantId(), id);
        Survey next = current.revise(new SurveyId(ids.next()), clock.instant());
        surveys.save(next);
        return next;
    }

    private Survey persist(Survey s) {
        surveys.save(s);
        events.publish(s.pullEvents());
        return s;
    }
}
