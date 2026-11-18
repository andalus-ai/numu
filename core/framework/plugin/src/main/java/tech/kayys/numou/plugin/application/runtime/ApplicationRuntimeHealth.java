package tech.kayys.numou.plugin.application.runtime;

import java.util.List;

/** Immutable point-in-time readiness snapshot for one application runtime session. */
public record ApplicationRuntimeHealth(
        String applicationId,
        boolean ready,
        boolean stopped,
        List<ApplicationRuntimeProviderHealth> providers) {
    public ApplicationRuntimeHealth {
        providers = List.copyOf(providers);
    }
}
