package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

/** Host extension point for supplying application-scoped context to each selected plugin. */
@FunctionalInterface
public interface ApplicationPluginContextFactory {
    PluginContext create(CapabilityProvider provider, NumouPlugin plugin);

    /**
     * Return a factory scoped to one application runtime. Custom factories may override this
     * method when they hold mutable registries; stateless factories and lambdas can keep the
     * default implementation.
     */
    default ApplicationPluginContextFactory forApplication(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("Application ID is required");
        }
        return this;
    }
}
