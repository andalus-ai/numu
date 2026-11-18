package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.PluginState;

/** Point-in-time health for one provider selected by an application composition. */
public record ApplicationRuntimeProviderHealth(
        String providerId,
        String pluginId,
        PluginState state,
        boolean ready) {
}
