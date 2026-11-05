package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Trusted, code-created application composition consumed by the runtime bootstrap.
 *
 * <p>Do not deserialize executable factories or Java class names from an
 * application manifest. Product bootstrap code constructs this definition from
 * validated configuration and trusted registrations.</p>
 */
public record RuntimeApplicationDefinition(
        ApplicationManifest manifest,
        List<CapabilityProvider> providers,
        CapabilityRuntimeFactory runtimeFactory) {

    public RuntimeApplicationDefinition {
        Objects.requireNonNull(manifest, "Application manifest is required");
        providers = List.copyOf(Objects.requireNonNull(providers, "Providers are required"));
        Objects.requireNonNull(runtimeFactory, "Trusted runtime factory is required");
    }

    public static RuntimeApplicationDefinition of(
            ApplicationManifest manifest,
            Collection<CapabilityProvider> providers,
            CapabilityRuntimeFactory runtimeFactory) {
        return new RuntimeApplicationDefinition(manifest, List.copyOf(providers), runtimeFactory);
    }
}
