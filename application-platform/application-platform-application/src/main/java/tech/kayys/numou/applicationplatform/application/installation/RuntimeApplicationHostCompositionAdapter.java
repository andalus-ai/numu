package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.plugin.api.Capability;
import tech.kayys.numou.plugin.api.NumouPlugin;
import tech.kayys.numou.plugin.api.PluginContext;
import tech.kayys.numou.plugin.api.PluginState;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process host composition adapter. It activates only host-registered NumouPlugin
 * factories in resolver dependency order and stops them in reverse order.
 *
 * Runtime instances are scoped to (tenant, installation), never shared across tenants.
 * Durable desired state remains the installation aggregate/outbox; this adapter is
 * reconstructed by replaying ENABLED installations after host restart.
 */
public final class RuntimeApplicationHostCompositionAdapter implements ApplicationHostCompositionPort {
    private final ResolvingApplicationCapabilityCompatibilityAdapter resolver;
    private final ApplicationCapabilityProviderRuntimeRegistry providers;
    private final ApplicationPluginContextFactory contexts;
    private final ConcurrentMap<InstallationKey, ActiveInstallation> active = new ConcurrentHashMap<>();
    private final ConcurrentMap<InstallationKey, Object> locks = new ConcurrentHashMap<>();

    public RuntimeApplicationHostCompositionAdapter(
            ResolvingApplicationCapabilityCompatibilityAdapter resolver,
            ApplicationCapabilityProviderRuntimeRegistry providers,
            ApplicationPluginContextFactory contexts) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    @Override
    public CompletionStage<Void> activate(ActivationCommand command) {
        if (command == null) return CompletableFuture.failedFuture(new IllegalArgumentException("command is required"));
        InstallationKey key = new InstallationKey(command.tenantId(), command.installationId());
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            ActiveInstallation existing = active.get(key);
            if (existing != null) {
                if (existing.applicationId.equals(command.applicationId())
                        && existing.operationIds.contains(command.operationId())) {
                    return CompletableFuture.completedFuture(null);
                }
                if (!existing.applicationId.equals(command.applicationId())) {
                    return CompletableFuture.failedFuture(
                            new IllegalStateException("Installation is already active for another application"));
                }
                return CompletableFuture.completedFuture(null);
            }
            try {
                CapabilityResolution resolution = resolver.resolve(command.tenantId(), command.applicationPackage())
                        .toCompletableFuture().join();
                List<StartedProvider> started = new ArrayList<>();
                try {
                    for (CapabilityProvider descriptor : resolution.initializationOrder()) {
                        ApplicationCapabilityProviderRegistration registration = providers.require(descriptor.providerId());
                        if (!registration.availableTo(command.tenantId())
                                || !registration.descriptor().equals(descriptor)) {
                            throw new SecurityException("Provider registration is not available or changed: "
                                    + descriptor.providerId());
                        }
                        NumouPlugin plugin = registration.createPlugin();
                        verifyPluginIdentity(plugin, descriptor);
                        PluginContext context = contexts.create(command.tenantId(), command.installationId(),
                                command.applicationId(), descriptor, command.correlationId());
                        boolean initialized = false;
                        try {
                            plugin.initialize(context);
                            initialized = true;
                            plugin.start();
                            if (plugin.state() != PluginState.ACTIVE) {
                                throw new IllegalStateException("Provider did not reach ACTIVE state: "
                                        + descriptor.providerId() + " (" + plugin.state() + ")");
                            }
                            started.add(new StartedProvider(descriptor, plugin, context));
                        } catch (Throwable failure) {
                            if (initialized) {
                                try { plugin.stop(); } catch (Throwable stopFailure) { failure.addSuppressed(stopFailure); }
                            }
                            throw failure;
                        }
                    }
                    active.put(key, new ActiveInstallation(command.applicationId(), started,
                            command.operationId()));
                    return CompletableFuture.completedFuture(null);
                } catch (Throwable failure) {
                    rollback(started, failure);
                    return CompletableFuture.failedFuture(failure);
                }
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(unwrap(failure));
            }
        }
    }

    @Override
    public CompletionStage<Void> deactivate(DeactivationCommand command) {
        if (command == null) return CompletableFuture.failedFuture(new IllegalArgumentException("command is required"));
        InstallationKey key = new InstallationKey(command.tenantId(), command.installationId());
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            ActiveInstallation installation = active.get(key);
            if (installation == null) return CompletableFuture.completedFuture(null);
            if (!installation.applicationId.equals(command.applicationId())) {
                return CompletableFuture.failedFuture(new SecurityException(
                        "Deactivation application does not match active installation"));
            }
            Throwable failure = stopReverse(installation.providers);
            if (failure == null) {
                active.remove(key, installation);
                return CompletableFuture.completedFuture(null);
            }
            return CompletableFuture.failedFuture(failure);
        }
    }

    public RuntimeHealth health(TenantId tenantId, UUID installationId) {
        ActiveInstallation installation = active.get(new InstallationKey(tenantId, installationId));
        if (installation == null) return RuntimeHealth.INACTIVE;
        return installation.providers.stream().allMatch(provider -> provider.plugin.state() == PluginState.ACTIVE)
                ? RuntimeHealth.READY : RuntimeHealth.DEGRADED;
    }

    private static void verifyPluginIdentity(NumouPlugin plugin, CapabilityProvider descriptor) {
        if (!descriptor.providerId().equals(plugin.id())) {
            throw new SecurityException("Host plugin ID does not match registered provider: " + descriptor.providerId());
        }
        boolean suppliesCapability = plugin.capabilities().stream().anyMatch(capability ->
                capability.name().equals(descriptor.capabilityId())
                        && capability.version().equals(descriptor.contractVersion()));
        if (!suppliesCapability) {
            throw new SecurityException("Host plugin does not advertise registered capability/version: "
                    + descriptor.providerId());
        }
    }

    private static void rollback(List<StartedProvider> started, Throwable failure) {
        Throwable rollbackFailure = stopReverse(started);
        if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
    }

    private static Throwable stopReverse(List<StartedProvider> providers) {
        Throwable failure = null;
        List<StartedProvider> reverse = new ArrayList<>(providers);
        Collections.reverse(reverse);
        for (StartedProvider provider : reverse) {
            try {
                provider.plugin.stop();
            } catch (Throwable stopFailure) {
                if (failure == null) failure = stopFailure;
                else failure.addSuppressed(stopFailure);
            }
        }
        return failure;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
    }

    private record InstallationKey(TenantId tenantId, UUID installationId) {
        private InstallationKey {
            Objects.requireNonNull(tenantId, "tenant");
            Objects.requireNonNull(installationId, "installation");
        }
    }

    private record StartedProvider(CapabilityProvider descriptor, NumouPlugin plugin, PluginContext context) { }

    private static final class ActiveInstallation {
        private final String applicationId;
        private final List<StartedProvider> providers;
        private final java.util.Set<UUID> operationIds = ConcurrentHashMap.newKeySet();

        private ActiveInstallation(String applicationId, List<StartedProvider> providers, UUID operationId) {
            this.applicationId = applicationId;
            this.providers = List.copyOf(providers);
            this.operationIds.add(operationId);
        }
    }

    public enum RuntimeHealth { INACTIVE, READY, DEGRADED }
}
