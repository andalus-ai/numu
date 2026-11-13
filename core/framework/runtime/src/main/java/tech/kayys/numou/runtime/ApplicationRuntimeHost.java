package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.manifest.ApplicationManifest;
import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;
import tech.kayys.numou.plugin.application.resolution.CapabilityProvider;
import tech.kayys.numou.plugin.application.runtime.ApplicationComposition;
import tech.kayys.numou.plugin.application.runtime.ApplicationCompositionService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Lifecycle host for one resolved application composition.
 *
 * <p>Manifest validation, capability resolution, provider authorization and
 * tenant installation policy belong to the application/control-plane boundary.
 * This class consumes the immutable composition plan and manages only trusted
 * runtime instances.</p>
 */
public final class ApplicationRuntimeHost implements AutoCloseable {
    private final ApplicationManifest manifest;
    private final ApplicationComposition composition;
    private final CapabilityRuntimeFactory runtimeFactory;
    private final List<CapabilityRuntime> started = new ArrayList<>();
    private ApplicationRuntimeState state = ApplicationRuntimeState.NEW;

    /**
     * Preferred constructor: consume a composition plan produced by the existing
     * ApplicationCompositionService. The host does not resolve capabilities again.
     */
    public ApplicationRuntimeHost(
            ApplicationManifest manifest,
            ApplicationComposition composition,
            CapabilityRuntimeFactory runtimeFactory) {
        this.manifest = Objects.requireNonNull(manifest, "Application manifest is required");
        this.composition = Objects.requireNonNull(composition, "Application composition is required");
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "Runtime factory is required");
        if (!manifest.id().equals(composition.applicationId())) {
            throw new IllegalArgumentException("Application composition ID does not match manifest: expected "
                    + manifest.id() + " but got " + composition.applicationId());
        }
        if (!manifest.version().equals(composition.applicationVersion())) {
            throw new IllegalArgumentException("Application composition version does not match manifest: expected "
                    + manifest.version() + " but got " + composition.applicationVersion());
        }
    }

    /**
     * Compatibility constructor for existing callers. New composition roots
     * should resolve once through ApplicationCompositionService and use the
     * composition-plan constructor.
     */
    @Deprecated(forRemoval = false)
    public ApplicationRuntimeHost(
            ApplicationManifest manifest,
            Collection<CapabilityProvider> availableProviders,
            ApplicationCapabilityResolver resolver,
            CapabilityRuntimeFactory runtimeFactory) {
        this(manifest,
                new ApplicationCompositionService(Objects.requireNonNull(resolver, "Capability resolver is required"))
                        .compose(manifest, availableProviders),
                runtimeFactory);
    }

    /**
     * Start selected providers in the dependency order recorded by the
     * immutable composition plan. A failed startup rolls back all attempted
     * runtimes in reverse order.
     */
    public synchronized void start() {
        if (state != ApplicationRuntimeState.NEW) {
            throw new IllegalStateException("Application runtime can only start from NEW; current state: " + state);
        }
        state = ApplicationRuntimeState.STARTING;
        try {
            for (CapabilityProvider provider : composition.initializationOrder()) {
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

    public List<String> unavailableOptionalCapabilities() {
        return composition.resolutionReport().unavailableOptionalCapabilities();
    }

    public ApplicationManifest manifest() {
        return manifest;
    }

    /** Immutable plan used by this host; useful for diagnostics and health reporting. */
    public ApplicationComposition composition() {
        return composition;
    }

    @Override
    public void close() {
        stop();
    }
}
