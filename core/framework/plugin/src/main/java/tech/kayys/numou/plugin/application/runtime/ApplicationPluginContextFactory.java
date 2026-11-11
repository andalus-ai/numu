package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

/** Host extension point for supplying application-scoped context to each selected plugin. */
@FunctionalInterface
public interface ApplicationPluginContextFactory {
    PluginContext create(CapabilityProvider provider, NumouPlugin plugin);
}
