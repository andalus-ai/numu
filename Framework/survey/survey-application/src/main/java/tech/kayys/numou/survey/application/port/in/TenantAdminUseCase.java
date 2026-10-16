package tech.kayys.numou.survey.application.port.in;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.tenant.Plan;
import tech.kayys.numou.survey.domain.tenant.Tenant;

import java.util.List;

/** Platform-operator operations (not tenant-scoped). */
public interface TenantAdminUseCase {
    Tenant register(RegisterTenant cmd);

    Tenant changePlan(TenantId id, Plan plan);

    Tenant deactivate(TenantId id);

    Tenant get(TenantId id);

    List<Tenant> list();

    record RegisterTenant(TenantId id, String name, Plan plan) {}
}
