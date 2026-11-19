package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.api.PluginState;

/** Point-in-time lifecycle and readiness details for one selected provider. */
public record ApplicationRuntimeProviderHealth(
        String providerId,
        String pluginId,
        PluginState state,
        boolean ready,
        String failure) {

    /** Compatibility constructor for callers that do not provide lifecycle diagnostics. */
    public ApplicationRuntimeProviderHealth(String providerId, String pluginId,
            PluginState state, boolean ready) {
        this(providerId, pluginId, state, ready, null);
    }
}
