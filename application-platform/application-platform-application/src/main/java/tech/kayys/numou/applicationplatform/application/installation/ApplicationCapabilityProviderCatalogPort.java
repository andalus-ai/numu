package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped view of capability providers exposed by the selected host runtime profile.
 * Implementations must return only providers actually available to this tenant and host.
 */
@FunctionalInterface
public interface ApplicationCapabilityProviderCatalogPort {
    CompletionStage<List<CapabilityProvider>> findAvailable(TenantId tenantId);
}
