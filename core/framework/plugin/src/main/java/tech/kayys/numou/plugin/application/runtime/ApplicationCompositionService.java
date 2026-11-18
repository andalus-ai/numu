package tech.kayys.numou.plugin.application.runtime;

import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolution;

import java.util.Collection;
import java.util.Objects;

/**
 * Application-level composition boundary shared by standalone and in-process hosts.
 * Runtime adapters may consume the returned plan; this service performs no lifecycle side effects.
 */
public final class ApplicationCompositionService {
    private final ApplicationCapabilityResolver resolver;

    public ApplicationCompositionService() {
        this(new ApplicationCapabilityResolver());
    }

    public ApplicationCompositionService(ApplicationCapabilityResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "Capability resolver is required");
    }

    public ApplicationComposition compose(ApplicationManifest manifest,
            Collection<CapabilityProvider> availableProviders) {
        Objects.requireNonNull(manifest, "Application manifest is required");
        CapabilityResolution resolution = resolver.resolve(manifest, availableProviders);
        return new ApplicationComposition(manifest.id(), manifest.version(),
                resolution.initializationOrder(), resolution.report());
    }
}
