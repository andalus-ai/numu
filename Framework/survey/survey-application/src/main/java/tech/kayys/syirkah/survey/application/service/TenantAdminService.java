package tech.kayys.syirkah.survey.application.service;

import tech.kayys.syirkah.survey.application.port.in.TenantAdminUseCase;
import tech.kayys.syirkah.survey.application.port.out.TenantRepository;
import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.survey.domain.tenant.Plan;
import tech.kayys.syirkah.survey.domain.tenant.Tenant;

import java.util.List;

public final class TenantAdminService implements TenantAdminUseCase {
    private final TenantRepository tenants;

    public TenantAdminService(TenantRepository tenants) { this.tenants = tenants; }

    @Override
    public Tenant register(RegisterTenant cmd) {
        if (tenants.findById(cmd.id()).isPresent())
            throw new DomainException(ErrorCode.CONFLICT, "Tenant already exists: " + cmd.id());
        Tenant t = Tenant.register(cmd.id(), cmd.name(), cmd.plan() == null ? Plan.FREE : cmd.plan());
        tenants.save(t);
        return t;
    }

    @Override
    public Tenant changePlan(TenantId id, Plan plan) {
        Tenant t = get(id).withPlan(plan);
        tenants.save(t);
        return t;
    }

    @Override
    public Tenant deactivate(TenantId id) {
        Tenant t = get(id).deactivate();
        tenants.save(t);
        return t;
    }

    @Override
    public Tenant get(TenantId id) {
        return tenants.findById(id).orElseThrow(() -> DomainException.notFound("tenant", id));
    }

    @Override
    public List<Tenant> list() { return tenants.findAll(); }
}
