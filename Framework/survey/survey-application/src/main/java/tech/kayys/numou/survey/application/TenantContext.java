package tech.kayys.numou.survey.application;

import tech.kayys.numou.survey.domain.Guard;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/** Who is calling and on behalf of which tenant. Passed explicitly into every use case: no thread-locals in the core. */
public record TenantContext(TenantId tenantId, String actorId) {
    public TenantContext {
        Guard.notNull(tenantId, "tenantId");
        actorId = actorId == null || actorId.isBlank() ? "anonymous" : actorId;
    }
}
