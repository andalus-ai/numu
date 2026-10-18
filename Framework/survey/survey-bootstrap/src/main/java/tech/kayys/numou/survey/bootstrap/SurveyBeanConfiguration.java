package tech.kayys.numou.survey.bootstrap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.survey.adapter.memory.InMemoryEventPublisher;
import tech.kayys.numou.survey.adapter.memory.InMemoryResponseRepository;
import tech.kayys.numou.survey.adapter.memory.InMemorySurveyRepository;
import tech.kayys.numou.survey.adapter.memory.InMemoryTenantRepository;
import tech.kayys.numou.survey.adapter.memory.UuidIdGenerator;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.application.port.in.ResultsUseCase;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase;
import tech.kayys.numou.survey.application.port.in.SurveyLifecycleUseCase;
import tech.kayys.numou.survey.application.port.out.EventPublisher;
import tech.kayys.numou.survey.application.port.out.IdGenerator;
import tech.kayys.numou.survey.application.port.out.ResponseRepository;
import tech.kayys.numou.survey.application.port.out.SurveyRepository;
import tech.kayys.numou.survey.application.port.out.TenantRepository;
import tech.kayys.numou.survey.application.service.ResponseService;
import tech.kayys.numou.survey.application.service.ResultsService;
import tech.kayys.numou.survey.application.service.SurveyAuthoringService;
import tech.kayys.numou.survey.application.service.SurveyLifecycleService;

import java.time.Clock;

/** CDI composition root for the development/test Survey runtime. */
@ApplicationScoped
public class SurveyBeanConfiguration {
    @Produces
    @ApplicationScoped
    public SurveyRepository surveyRepository() {
        return new InMemorySurveyRepository();
    }

    @Produces
    @ApplicationScoped
    public ResponseRepository responseRepository() {
        return new InMemoryResponseRepository();
    }

    @Produces
    @ApplicationScoped
    public TenantRepository tenantRepository() {
        return new InMemoryTenantRepository();
    }

    @Produces
    @ApplicationScoped
    public EventPublisher eventPublisher() {
        return new InMemoryEventPublisher();
    }

    @Produces
    @ApplicationScoped
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Produces
    @ApplicationScoped
    public SurveyAuthoringUseCase surveyAuthoringUseCase(
            SurveyRepository surveys, TenantRepository tenants, IdGenerator ids, Clock clock) {
        return new SurveyAuthoringService(surveys, tenants, ids, clock);
    }

    @Produces
    @ApplicationScoped
    public SurveyLifecycleUseCase surveyLifecycleUseCase(
            SurveyRepository surveys, TenantRepository tenants, EventPublisher events, IdGenerator ids, Clock clock) {
        return new SurveyLifecycleService(surveys, tenants, events, ids, clock);
    }

    @Produces
    @ApplicationScoped
    public ResponseUseCase responseUseCase(
            SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants,
            EventPublisher events, IdGenerator ids, Clock clock) {
        return new ResponseService(surveys, responses, tenants, events, ids, clock);
    }

    @Produces
    @ApplicationScoped
    public ResultsUseCase resultsUseCase(
            SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants) {
        return new ResultsService(surveys, responses, tenants);
    }
}
