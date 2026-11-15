package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.concurrent.CompletionStage;

/** Host-owned authorization; tenant and actor must come from trusted authentication context. */
public interface ApplicationInstallationOperatorAuthorizationPort {
    CompletionStage<Void> assertCanInspect(TenantId tenantId, String actorId);
    CompletionStage<Void> assertCanRedrive(TenantId tenantId, String actorId);
}
