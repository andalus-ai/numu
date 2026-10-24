package tech.kayys.numou.omnichannel.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable, validated registry of provider-specific webhook verifiers.
 * Duplicate provider IDs fail construction rather than silently overriding
 * one adapter with another.
 */
public final class ProviderWebhookAdapterRegistry {
    private final Map<String, ProviderWebhookAdapterRegistration> registrations;

    public ProviderWebhookAdapterRegistry(Collection<ProviderWebhookAdapterRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations must not be null");
        this.registrations = registrations.stream()
                .map(registration -> Objects.requireNonNull(registration, "registration must not be null"))
                .collect(Collectors.toUnmodifiableMap(
                        registration -> registration.capabilities().providerId(),
                        Function.identity()));
    }

    public boolean supports(String providerId) {
        return registrations.containsKey(requiredProviderId(providerId));
    }

    public ProviderWebhookVerifier verifierFor(String providerId) {
        ProviderWebhookAdapterRegistration registration = registrations.get(requiredProviderId(providerId));
        return registration == null ? null : registration.verifier();
    }

    public ProviderWebhookCapabilities capabilitiesFor(String providerId) {
        ProviderWebhookAdapterRegistration registration = registrations.get(requiredProviderId(providerId));
        return registration == null ? null : registration.capabilities();
    }

    /** Immutable snapshot suitable for wiring the registry into ingestion. */
    public List<ProviderWebhookVerifier> verifiers() {
        return registrations.values().stream()
                .map(ProviderWebhookAdapterRegistration::verifier)
                .toList();
    }

    public int size() {
        return registrations.size();
    }

    private static String requiredProviderId(String value) {
        Objects.requireNonNull(value, "providerId must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException("providerId must contain 1 to 200 characters");
        }
        return normalized;
    }
}
