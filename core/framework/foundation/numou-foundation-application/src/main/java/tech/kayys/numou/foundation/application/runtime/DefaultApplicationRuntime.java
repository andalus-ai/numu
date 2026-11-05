package tech.kayys.numou.foundation.application.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.availability.AvailabilityReason;
import tech.kayys.numou.foundation.application.availability.Liveness;
import tech.kayys.numou.foundation.application.availability.LivenessStatus;
import tech.kayys.numou.foundation.application.availability.Readiness;
import tech.kayys.numou.foundation.application.availability.ReadinessStatus;
import tech.kayys.numou.foundation.application.startup.StartupValidationContext;
import tech.kayys.numou.foundation.application.startup.StartupValidationException;
import tech.kayys.numou.foundation.application.startup.StartupValidationReport;
import tech.kayys.numou.foundation.application.startup.StartupValidator;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * Standard implementation of {@link ApplicationRuntime} executing the canonical lifecycle state machine:
 * NEW -> CONFIGURING -> VALIDATING -> STARTING -> RUNNING -> STOPPING -> STOPPED / FAILED (config01.md §P4-05).
 */
public final class DefaultApplicationRuntime implements ApplicationRuntime, Liveness, Readiness {

    private final RuntimeRegistry registry;
    private final ModuleRegistry moduleRegistry;
    private final ApplicationComponentRegistry componentRegistry;
    private final StartupValidator startupValidator;
    private final List<ManagedWorker> workers;
    private final Consumer<ApplicationRuntimeState> stateListener;

    private volatile ApplicationRuntimeState state = ApplicationRuntimeState.NEW;
    private volatile Uni<Void> startupOperation;
    private volatile Uni<Void> shutdownOperation;

    private final Deque<RuntimeComponent> startedComponents = new ArrayDeque<>();
    private final Deque<ManagedWorker> startedWorkers = new ArrayDeque<>();
    private final List<LifecycleTransition> transitions = new ArrayList<>();
    private final List<AvailabilityReason> availabilityReasons = new ArrayList<>();

    public DefaultApplicationRuntime(
            RuntimeRegistry registry,
            ModuleRegistry moduleRegistry,
            ApplicationComponentRegistry componentRegistry,
            StartupValidator startupValidator,
            Collection<ManagedWorker> workers) {
        this(registry, moduleRegistry, componentRegistry, startupValidator, workers, ignored -> { });
    }

    public DefaultApplicationRuntime(
            RuntimeRegistry registry,
            ModuleRegistry moduleRegistry,
            ApplicationComponentRegistry componentRegistry,
            StartupValidator startupValidator,
            Collection<ManagedWorker> workers,
            Consumer<ApplicationRuntimeState> stateListener) {

        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.moduleRegistry = Objects.requireNonNull(moduleRegistry, "moduleRegistry cannot be null");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry cannot be null");
        this.startupValidator = Objects.requireNonNull(startupValidator, "startupValidator cannot be null");
        this.workers = workers != null ? new ArrayList<>(workers) : new ArrayList<>();
        this.stateListener = Objects.requireNonNull(stateListener, "stateListener cannot be null");
        validateWorkerIds(this.workers);
    }

    private static void validateWorkerIds(Collection<ManagedWorker> workers) {
        Set<String> ids = new HashSet<>();
        for (ManagedWorker worker : workers) {
            Objects.requireNonNull(worker, "worker cannot be null");
            String id = worker.id();
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("worker ID cannot be blank");
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("Managed worker is already registered: " + id);
            }
        }
    }

    @Override
    public ApplicationRuntimeState state() {
        return state;
    }

    @Override
    public boolean isRunning() {
        return state == ApplicationRuntimeState.RUNNING;
    }

    @Override
    public RuntimeRegistry registry() {
        return registry;
    }

    @Override
    public synchronized Uni<Void> start() {
        if (state == ApplicationRuntimeState.RUNNING) {
            return Uni.createFrom().voidItem();
        }
        if (state == ApplicationRuntimeState.STARTING && startupOperation != null) {
            return startupOperation;
        }
        if (state != ApplicationRuntimeState.NEW) {
            return Uni.createFrom().failure(new LifecycleTransitionException(
                    "Cannot start ApplicationRuntime from state: " + state));
        }

        transition(ApplicationRuntimeState.CONFIGURING);

        startupOperation = Uni.createFrom().item(() -> {
            executeStartupSequence();
            return (Void) null;
        });

        return startupOperation;
    }

    private synchronized void executeStartupSequence() {
        try {
            // 1. VALIDATING
            transition(ApplicationRuntimeState.VALIDATING);
            StartupValidationContext ctx = new DefaultStartupValidationContext(
                    registry.configuration().snapshot(),
                    null,
                    registry,
                    moduleRegistry,
                    componentRegistry,
                    state
            );
            StartupValidationReport report = startupValidator.validate(ctx);
            if (!report.passed()) {
                throw new StartupValidationException(report);
            }

            // 2. STARTING
            transition(ApplicationRuntimeState.STARTING);

            // Start components in order
            for (RuntimeComponent comp : componentRegistry.components()) {
                comp.start().await().atMost(registry.settings().startupTimeout());
                startedComponents.push(comp);
            }

            // Start workers
            for (ManagedWorker worker : workers) {
                worker.start().await().atMost(registry.settings().startupTimeout());
                startedWorkers.push(worker);
            }

            // 3. RUNNING
            transition(ApplicationRuntimeState.RUNNING);
            availabilityReasons.clear();
        } catch (Throwable t) {
            rollbackStartup(t);
            transition(ApplicationRuntimeState.FAILED);
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new ApplicationRuntimeException("Startup sequence failed", t);
        }
    }

    private void rollbackStartup(Throwable cause) {
        // Stop started workers in reverse order and retain cleanup failures without masking the startup cause.
        while (!startedWorkers.isEmpty()) {
            ManagedWorker worker = startedWorkers.pop();
            try {
                worker.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (RuntimeException cleanupFailure) {
                availabilityReasons.add(cleanupFailure("STARTUP_ROLLBACK_FAILURE", worker.id(), cleanupFailure));
            }
        }
        // Stop started components in reverse order.
        while (!startedComponents.isEmpty()) {
            RuntimeComponent component = startedComponents.pop();
            try {
                component.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (RuntimeException cleanupFailure) {
                availabilityReasons.add(cleanupFailure("STARTUP_ROLLBACK_FAILURE", component.id(), cleanupFailure));
            }
        }
        availabilityReasons.add(AvailabilityReason.of(
                "STARTUP_FAILURE",
                "Startup failed (" + cause.getClass().getSimpleName() + ")"
        ));
    }

    @Override
    public synchronized Uni<Void> stop() {
        if (state == ApplicationRuntimeState.STOPPED) {
            return Uni.createFrom().voidItem();
        }
        if (state == ApplicationRuntimeState.STOPPING && shutdownOperation != null) {
            return shutdownOperation;
        }
        if (state != ApplicationRuntimeState.RUNNING && state != ApplicationRuntimeState.STARTING) {
            return Uni.createFrom().failure(new LifecycleTransitionException(
                    "Cannot stop ApplicationRuntime from state: " + state));
        }

        transition(ApplicationRuntimeState.STOPPING);
        availabilityReasons.add(AvailabilityReason.of("SHUTDOWN_IN_PROGRESS", "Application is shutting down"));

        shutdownOperation = Uni.createFrom().item(() -> {
            executeShutdownSequence();
            return (Void) null;
        });

        return shutdownOperation;
    }

    private synchronized void executeShutdownSequence() {
        List<RuntimeException> failures = new ArrayList<>();

        // Stop workers first, continuing after failures so all resources get a cleanup attempt.
        while (!startedWorkers.isEmpty()) {
            ManagedWorker worker = startedWorkers.pop();
            try {
                worker.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (RuntimeException failure) {
                failures.add(failure);
                availabilityReasons.add(cleanupFailure("SHUTDOWN_FAILURE", worker.id(), failure));
            }
        }

        // Stop components in reverse startup order.
        while (!startedComponents.isEmpty()) {
            RuntimeComponent component = startedComponents.pop();
            try {
                component.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (RuntimeException failure) {
                failures.add(failure);
                availabilityReasons.add(cleanupFailure("SHUTDOWN_FAILURE", component.id(), failure));
            }
        }

        if (!failures.isEmpty()) {
            transition(ApplicationRuntimeState.FAILED);
            throw new ApplicationRuntimeException(
                    "Runtime shutdown completed with " + failures.size() + " cleanup failure(s)",
                    failures.get(0)
            );
        }
        transition(ApplicationRuntimeState.STOPPED);
    }

    private static AvailabilityReason cleanupFailure(String code, String componentId, RuntimeException failure) {
        return AvailabilityReason.of(
                code,
                componentId + " cleanup failed (" + failure.getClass().getSimpleName() + ")"
        );
    }

    private void transition(ApplicationRuntimeState next) {
        LifecycleTransition trans = LifecycleTransition.of(state, next);
        transitions.add(trans);
        state = next;
        stateListener.accept(next);
    }

    // Availability contracts
    @Override
    public LivenessStatus livenessStatus() {
        return state != ApplicationRuntimeState.FAILED ? LivenessStatus.ALIVE : LivenessStatus.DEAD;
    }

    @Override
    public ReadinessStatus readinessStatus() {
        return state == ApplicationRuntimeState.RUNNING ? ReadinessStatus.READY : ReadinessStatus.NOT_READY;
    }

    @Override
    public boolean isReady() {
        return state == ApplicationRuntimeState.RUNNING;
    }

    @Override
    public List<AvailabilityReason> reasons() {
        return Collections.unmodifiableList(new ArrayList<>(availabilityReasons));
    }

    public List<LifecycleTransition> history() {
        return Collections.unmodifiableList(new ArrayList<>(transitions));
    }

    private record DefaultStartupValidationContext(
            tech.kayys.numou.foundation.application.config.ConfigurationSnapshot configuration,
            tech.kayys.numou.foundation.application.config.profile.ResolvedProfile profile,
            RuntimeRegistry runtime,
            ModuleRegistry modules,
            ApplicationComponentRegistry components,
            ApplicationRuntimeState runtimeState
    ) implements StartupValidationContext {}
}
