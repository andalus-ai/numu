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

        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.moduleRegistry = Objects.requireNonNull(moduleRegistry, "moduleRegistry cannot be null");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry cannot be null");
        this.startupValidator = Objects.requireNonNull(startupValidator, "startupValidator cannot be null");
        this.workers = workers != null ? new ArrayList<>(workers) : new ArrayList<>();
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
        // Stop started workers in reverse order
        while (!startedWorkers.isEmpty()) {
            ManagedWorker worker = startedWorkers.pop();
            try {
                worker.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (Exception ignored) {}
        }
        // Stop started components in reverse order
        while (!startedComponents.isEmpty()) {
            RuntimeComponent comp = startedComponents.pop();
            try {
                comp.stop().await().atMost(registry.settings().shutdownTimeout());
            } catch (Exception ignored) {}
        }
        availabilityReasons.add(AvailabilityReason.of("STARTUP_FAILURE", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()));
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
        try {
            // Stop workers first
            while (!startedWorkers.isEmpty()) {
                ManagedWorker worker = startedWorkers.pop();
                try {
                    worker.stop().await().atMost(registry.settings().shutdownTimeout());
                } catch (Exception ignored) {}
            }
            // Stop components
            while (!startedComponents.isEmpty()) {
                RuntimeComponent comp = startedComponents.pop();
                try {
                    comp.stop().await().atMost(registry.settings().shutdownTimeout());
                } catch (Exception ignored) {}
            }
            transition(ApplicationRuntimeState.STOPPED);
        } catch (Throwable t) {
            transition(ApplicationRuntimeState.FAILED);
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new ApplicationRuntimeException("Shutdown sequence failed", t);
        }
    }

    private void transition(ApplicationRuntimeState next) {
        LifecycleTransition trans = LifecycleTransition.of(state, next);
        transitions.add(trans);
        state = next;
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
