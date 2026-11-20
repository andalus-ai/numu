package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable allowlist mapping resolved provider IDs to trusted runtime factories.
 *
 * <p>Registrations are supplied by the application composition root. The catalog
 * never loads classes, interprets manifest class names, scans the classpath, or
 * executes code supplied by an application descriptor.</p>
 */
public final class TrustedCapabilityRuntimeCatalog implements CapabilityRuntimeFactory {
    private final Map<String, CapabilityRuntimeFactory> factories;

    private TrustedCapabilityRuntimeCatalog(Map<String, CapabilityRuntimeFactory> factories) {
        this.factories = Collections.unmodifiableMap(new TreeMap<>(factories));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CapabilityRuntime create(CapabilityProvider provider) {
        Objects.requireNonNull(provider, "Resolved capability provider is required");
        CapabilityRuntimeFactory factory = factories.get(provider.providerId());
        if (factory == null) {
            throw new ApplicationRuntimeException(
                    "No trusted runtime factory registered for provider " + provider.providerId());
        }
        CapabilityRuntime runtime = Objects.requireNonNull(factory.create(provider),
                "Trusted runtime factory returned null for provider " + provider.providerId());
        if (!provider.providerId().equals(runtime.providerId())) {
            throw new ApplicationRuntimeException("Trusted runtime factory identity mismatch: expected "
                    + provider.providerId() + " but got " + runtime.providerId());
        }
        return runtime;
    }

    public boolean supports(String providerId) {
        return factories.containsKey(providerId);
    }

    public Set<String> providerIds() {
        return factories.keySet();
    }

    /** Mutable builder used only during trusted application composition. */
    public static final class Builder {
        private final Map<String, CapabilityRuntimeFactory> factories = new TreeMap<>();

        public Builder register(String providerId, CapabilityRuntimeFactory factory) {
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("Provider ID is required");
            }
            Objects.requireNonNull(factory, "Runtime factory is required");
            if (factories.putIfAbsent(providerId, factory) != null) {
                throw new IllegalArgumentException("Duplicate trusted runtime factory for provider " + providerId);
            }
            return this;
        }

        public TrustedCapabilityRuntimeCatalog build() {
            return new TrustedCapabilityRuntimeCatalog(factories);
        }
    }
}
