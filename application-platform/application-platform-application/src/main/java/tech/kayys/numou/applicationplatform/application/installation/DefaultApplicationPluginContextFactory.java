package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.api.DefaultPluginContext;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Map;
import java.util.UUID;

/** Default isolated context; host adapters may supply services through a custom factory. */
public final class DefaultApplicationPluginContextFactory implements ApplicationPluginContextFactory {
    @Override
    public PluginContext create(TenantId tenantId, UUID installationId, String applicationId,
            CapabilityProvider provider, String correlationId) {
        var context = new DefaultPluginContext(provider.providerId());
        context.setAttribute("numou.tenant.id", tenantId.toString());
        context.setAttribute("numou.application.installation_id", installationId.toString());
        context.setAttribute("numou.application.id", applicationId);
        context.setAttribute("numou.capability.id", provider.capabilityId());
        context.setAttribute("numou.capability.contract_version", provider.contractVersion());
        context.setAttribute("numou.correlation_id", correlationId == null ? "" : correlationId);
        return context;
    }
}
