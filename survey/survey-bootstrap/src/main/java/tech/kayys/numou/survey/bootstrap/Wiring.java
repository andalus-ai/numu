package tech.kayys.numou.survey.bootstrap;

import tech.kayys.numou.survey.adapter.memory.*;
import tech.kayys.numou.survey.application.port.in.*;
import tech.kayys.numou.survey.application.port.out.*;
import tech.kayys.numou.survey.application.service.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * The only place that knows which adapter implements which port.
 * To go to production: replace the in-memory producers with e.g. Panache/JDBC repositories and an outbox publisher.
 */
@ApplicationScoped
public class Wiring {

    // ---- outbound adapters
    @Produces @Singleton SurveyRepository surveyRepository() { return new InMemorySurveyRepository(); }
    @Produces @Singleton ResponseRepository responseRepository() { return new InMemoryResponseRepository(); }
    @Produces @Singleton TenantRepository tenantRepository() { return new InMemoryTenantRepository(); }
    @Produces @Singleton EventPublisher eventPublisher() { return new CollectingEventPublisher(); }
    @Produces @Singleton IdGenerator idGenerator() { return new UuidIdGenerator(); }
    @Produces @Singleton Clock clock() { return Clock.systemUTC(); }

    // ---- application services exposed as inbound ports
    @Produces @Singleton
    TenantAdminUseCase tenantAdmin(TenantRepository tenants) { return new TenantAdminService(tenants); }

    @Produces @Singleton
    SurveyAuthoringUseCase authoring(SurveyRepository surveys, TenantRepository tenants, IdGenerator ids, Clock clock) {
        return new SurveyAuthoringService(surveys, tenants, ids, clock);
    }

    @Produces @Singleton
    SurveyLifecycleUseCase lifecycle(SurveyRepository surveys, TenantRepository tenants, EventPublisher events, IdGenerator ids, Clock clock) {
        return new SurveyLifecycleService(surveys, tenants, events, ids, clock);
    }

    @Produces @Singleton
    ResponseUseCase responses(SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants,
                              EventPublisher events, IdGenerator ids, Clock clock) {
        return new ResponseService(surveys, responses, tenants, events, ids, clock);
    }

    @Produces @Singleton
    ResultsUseCase results(SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants) {
        return new ResultsService(surveys, responses, tenants);
    }
}
