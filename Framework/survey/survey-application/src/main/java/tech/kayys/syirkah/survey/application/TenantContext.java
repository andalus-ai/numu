package tech.kayys.syirkah.survey.application;

import tech.kayys.syirkah.survey.domain.Guard;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

/** Who is calling and on behalf of which tenant. Passed explicitly into every use case: no thread-locals in the core. */
public record TenantContext(TenantId tenantId, String actorId) {
    public TenantContext {
        Guard.notNull(tenantId, "tenantId");
        actorId = actorId == null || actorId.isBlank() ? "anonymous" : actorId;
    }
}
