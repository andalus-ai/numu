package tech.kayys.numou.runtime;

import tech.kayys.numou.plugin.application.resolution.ApplicationCapabilityResolver;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Process-level lifecycle facade for one application composition.
 *
 * <p>Product launchers own HTTP servers, dependency injection and external
 * adapters. They construct this bootstrap with a trusted definition, then
 * expose {@link #health()} through their chosen health transport.</p>
 */
public final class ApplicationRuntimeBootstrap implements AutoCloseable {
    private final RuntimeConfiguration configuration;
    private final RuntimeApplicationDefinition definition;
    private final ApplicationCapabilityResolver resolver;
    private final ApplicationRuntimeHost host;

    private RuntimeReadiness readiness = RuntimeReadiness.STARTING;
    private Throwable failure;
    private Thread shutdownHook;

    public ApplicationRuntimeBootstrap(
            RuntimeConfiguration configuration,
            RuntimeApplicationDefinition definition) {
        this(configuration, definition, new ApplicationCapabilityResolver());
    }

    public ApplicationRuntimeBootstrap(
            RuntimeConfiguration configuration,
            RuntimeApplicationDefinition definition,
            ApplicationCapabilityResolver resolver) {
        this.configuration = Objects.requireNonNull(configuration, "Runtime configuration is required");
        this.definition = Objects.requireNonNull(definition, "Application definition is required");
        this.resolver = Objects.requireNonNull(resolver, "Capability resolver is required");
        if (!configuration.applicationId().equals(definition.manifest().id())) {
            throw new IllegalArgumentException("Configured application ID "
                    + configuration.applicationId() + " does not match manifest "
                    + definition.manifest().id());
        }
        this.host = new ApplicationRuntimeHost(definition.manifest(), definition.providers(),
                resolver, definition.runtimeFactory());
    }

    /** Starts the selected application composition exactly once. */
    public synchronized void start() {
        if (readiness != RuntimeReadiness.STARTING) {
            throw new IllegalStateException("Bootstrap can only start from STARTING; current state: " + readiness);
        }
        try {
            host.start();
            readiness = RuntimeReadiness.READY;
        } catch (RuntimeException startupFailure) {
            failure = startupFailure;
            readiness = RuntimeReadiness.FAILED;
            throw startupFailure;
        }
    }

    /**
     * Installs an optional JVM shutdown hook. The hook is deliberately opt-in so
     * embedding applications and tests can own process lifecycle themselves.
     */
    public synchronized void installShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        if (readiness == RuntimeReadiness.STOPPED) {
            throw new IllegalStateException("Cannot install a shutdown hook after the runtime has stopped");
        }
        shutdownHook = new Thread(this::close, "numou-runtime-shutdown-" + configuration.applicationId());
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized RuntimeHealthSnapshot health() {
        List<String> active = host.activeProviderIds();
        boolean live = readiness != RuntimeReadiness.STOPPED;
        boolean ready = readiness == RuntimeReadiness.READY;
        return new RuntimeHealthSnapshot(configuration.applicationId(), configuration.profile(),
                readiness, live, ready, active, host.unavailableOptionalCapabilities(),
                Instant.now(), failure == null ? null : failure.getClass().getSimpleName());
    }

    public synchronized void close() {
        if (readiness == RuntimeReadiness.STOPPED) {
            return;
        }
        if (readiness == RuntimeReadiness.STARTING && host.state() == ApplicationRuntimeState.NEW) {
            readiness = RuntimeReadiness.STOPPED;
            removeShutdownHook();
            return;
        }
        readiness = RuntimeReadiness.STOPPING;
        try {
            host.stop();
            readiness = RuntimeReadiness.STOPPED;
        } catch (RuntimeException stopFailure) {
            failure = stopFailure;
            readiness = RuntimeReadiness.FAILED;
            throw stopFailure;
        } finally {
            removeShutdownHook();
        }
    }

    private void removeShutdownHook() {
        Thread hook = shutdownHook;
        shutdownHook = null;
        if (hook == null || Thread.currentThread() == hook) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM shutdown is already in progress.
        }
    }
}
