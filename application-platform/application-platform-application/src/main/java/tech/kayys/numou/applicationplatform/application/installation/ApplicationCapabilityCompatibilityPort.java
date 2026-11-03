package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.concurrent.CompletionStage;

/** Resolves manifest requirements against this tenant's selected host runtime profile. */
@FunctionalInterface
public interface ApplicationCapabilityCompatibilityPort {
    CompletionStage<Void> assertCompatible(TenantId tenantId, ValidatedApplicationPackage applicationPackage);
}
