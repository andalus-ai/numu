package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.UUID;

/** Creates an isolated plugin context for one tenant/application installation/provider. */
@FunctionalInterface
public interface ApplicationPluginContextFactory {
    PluginContext create(TenantId tenantId, UUID installationId, String applicationId,
            CapabilityProvider provider, String correlationId);
}
