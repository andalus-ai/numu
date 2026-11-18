package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.api.NumouPlugin;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

/**
 * Trusted host registration binding a resolved provider descriptor to a factory.
 * The factory is host code; application packages can never register executable code.
 */
public record ApplicationCapabilityProviderRegistration(
        CapabilityProvider descriptor,
        Predicate<TenantId> tenantAvailability,
        Supplier<? extends NumouPlugin> pluginFactory) {
    public ApplicationCapabilityProviderRegistration {
        Objects.requireNonNull(descriptor, "provider descriptor");
        Objects.requireNonNull(tenantAvailability, "tenant availability predicate");
        Objects.requireNonNull(pluginFactory, "host plugin factory");
    }

    public boolean availableTo(TenantId tenantId) {
        return tenantAvailability.test(tenantId);
    }

    public NumouPlugin createPlugin() {
        return Objects.requireNonNull(pluginFactory.get(),
                "Host provider factory returned null for " + descriptor.providerId());
    }
}
