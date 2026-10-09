package tech.kayys.syirkah.survey.application.service;

import tech.kayys.syirkah.survey.application.port.out.SurveyRepository;
import tech.kayys.syirkah.survey.application.port.out.TenantRepository;
import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.survey.domain.survey.Survey;
import tech.kayys.syirkah.survey.domain.tenant.Tenant;

/** Shared guard: resolve the tenant, make sure it is active, load tenant-scoped aggregates. */
final class TenantGate {
    private final TenantRepository tenants;
    private final SurveyRepository surveys;

    TenantGate(TenantRepository tenants, SurveyRepository surveys) {
        this.tenants = tenants;
        this.surveys = surveys;
    }

    Tenant activeTenant(TenantId id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> DomainException.notFound("tenant", id));
        t.ensureActive();
        return t;
    }

    Survey survey(TenantId tenant, SurveyId id) {
        return surveys.findById(tenant, id).orElseThrow(() -> DomainException.notFound("survey", id));
    }
}
