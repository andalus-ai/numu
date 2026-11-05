package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.resolution.CapabilityResolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Lifecycle host for one validated application composition.
 *
 * <p>Manifest validation and provider authorization are performed by the
 * application/control-plane boundary before this host is constructed. This
 * class only resolves trusted provider metadata and manages runtime instances.</p>
 */
public final class ApplicationRuntimeHost implements AutoCloseable {
    private final ApplicationManifest manifest;
    private final Collection<CapabilityProvider> availableProviders;
    private final ApplicationCapabilityResolver resolver;
    private final CapabilityRuntimeFactory runtimeFactory;
    private final List<CapabilityRuntime> started = new ArrayList<>();
    private ApplicationRuntimeState state = ApplicationRuntimeState.NEW;
    private CapabilityResolution resolution;

    public ApplicationRuntimeHost(
            ApplicationManifest manifest,
            Collection<CapabilityProvider> availableProviders,
            ApplicationCapabilityResolver resolver,
            CapabilityRuntimeFactory runtimeFactory) {
        this.manifest = Objects.requireNonNull(manifest, "Application manifest is required");
        this.availableProviders = List.copyOf(Objects.requireNonNull(availableProviders,
                "Available providers are required"));
        this.resolver = Objects.requireNonNull(resolver, "Capability resolver is required");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "Runtime factory is required");
    }

    /**
     * Resolve and start the selected composition in dependency order.
     * A failed startup rolls back every successfully started runtime in reverse order.
     */
    public synchronized void start() {
        if (state != ApplicationRuntimeState.NEW) {
            throw new IllegalStateException("Application runtime can only start from NEW; current state: " + state);
        }
        state = ApplicationRuntimeState.STARTING;
        try {
            resolution = resolver.resolve(manifest, availableProviders);
            for (CapabilityProvider provider : resolution.initializationOrder()) {
                CapabilityRuntime runtime = Objects.requireNonNull(runtimeFactory.create(provider),
                        "Runtime factory returned null for provider " + provider.providerId());
                if (!provider.providerId().equals(runtime.providerId())) {
                    throw new IllegalStateException("Runtime provider mismatch: expected "
                            + provider.providerId() + " but got " + runtime.providerId());
                }
                // Track before start so rollback also releases resources acquired by a
                // provider whose start method fails part-way through initialization.
                started.add(runtime);
                runtime.start();
            }
            state = ApplicationRuntimeState.RUNNING;
        } catch (Exception failure) {
            rollback(failure);
            state = ApplicationRuntimeState.FAILED;
            throw new ApplicationRuntimeException(
                    "Failed to start application runtime " + manifest.id(), failure);
        }
    }

    /** Stop started providers in reverse dependency order. Safe to call repeatedly. */
    public synchronized void stop() {
        if (state == ApplicationRuntimeState.STOPPED || state == ApplicationRuntimeState.NEW) {
            state = ApplicationRuntimeState.STOPPED;
            return;
        }
        if (state == ApplicationRuntimeState.FAILED) {
            return; // startup rollback has already been attempted
        }
        if (state != ApplicationRuntimeState.RUNNING) {
            throw new IllegalStateException("Application runtime cannot stop from state: " + state);
        }
        state = ApplicationRuntimeState.STOPPING;
        ApplicationRuntimeException failure = null;
        for (int i = started.size() - 1; i >= 0; i--) {
            try {
                started.get(i).stop();
            } catch (Exception stopFailure) {
                if (failure == null) {
                    failure = new ApplicationRuntimeException(
                            "One or more capability runtimes failed to stop for " + manifest.id());
                }
                failure.addSuppressed(stopFailure);
            }
        }
        started.clear();
        state = ApplicationRuntimeState.STOPPED;
        if (failure != null) {
            throw failure;
        }
    }

    private void rollback(Exception startupFailure) {
        for (int i = started.size() - 1; i >= 0; i--) {
            try {
                started.get(i).stop();
            } catch (Exception rollbackFailure) {
                startupFailure.addSuppressed(rollbackFailure);
            }
        }
        started.clear();
    }

    public synchronized ApplicationRuntimeState state() {
        return state;
    }

    public synchronized List<String> activeProviderIds() {
        return started.stream().map(CapabilityRuntime::providerId).toList();
    }

    public synchronized List<String> unavailableOptionalCapabilities() {
        return resolution == null ? List.of() : resolution.unavailableOptionalCapabilities();
    }

    public ApplicationManifest manifest() {
        return manifest;
    }

    @Override
    public void close() {
        stop();
    }
}
