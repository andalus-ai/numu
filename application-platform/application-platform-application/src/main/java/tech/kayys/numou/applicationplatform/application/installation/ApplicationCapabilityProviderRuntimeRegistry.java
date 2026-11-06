package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for host-approved provider descriptors and their factories.
 * A registry instance represents one immutable host runtime profile; tenant predicates
 * further restrict which registered providers are visible to a tenant.
 */
public final class ApplicationCapabilityProviderRuntimeRegistry
        implements ApplicationCapabilityProviderCatalogPort {
    private final Map<String, ApplicationCapabilityProviderRegistration> registrations =
            new ConcurrentHashMap<>();

    public void register(ApplicationCapabilityProviderRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        var descriptor = registration.descriptor();
        var previous = registrations.putIfAbsent(descriptor.providerId(), registration);
        if (previous != null && !previous.equals(registration)) {
            throw new IllegalStateException("Conflicting provider registration: " + descriptor.providerId());
        }
    }

    public boolean unregister(String providerId, String expectedCapabilityId) {
        if (providerId == null || expectedCapabilityId == null) return false;
        var current = registrations.get(providerId);
        return current != null
                && current.descriptor().capabilityId().equals(expectedCapabilityId)
                && registrations.remove(providerId, current);
    }

    public ApplicationCapabilityProviderRegistration require(String providerId) {
        var registration = registrations.get(providerId);
        if (registration == null) {
            throw new IllegalStateException("Resolved provider is not registered by this host: " + providerId);
        }
        return registration;
    }

    @Override
    public CompletionStage<List<CapabilityProvider>> findAvailable(TenantId tenantId) {
        if (tenantId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenant is required"));
        }
        List<CapabilityProvider> available = new ArrayList<>();
        registrations.values().stream()
                .filter(registration -> registration.availableTo(tenantId))
                .map(ApplicationCapabilityProviderRegistration::descriptor)
                .sorted(Comparator.comparing(CapabilityProvider::providerId))
                .forEach(available::add);
        return CompletableFuture.completedFuture(List.copyOf(available));
    }
}
