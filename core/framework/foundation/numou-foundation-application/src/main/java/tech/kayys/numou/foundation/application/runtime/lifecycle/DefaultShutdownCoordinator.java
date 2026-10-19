package tech.kayys.numou.foundation.application.runtime.lifecycle;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.availability.Readiness;
import tech.kayys.numou.foundation.application.runtime.ManagedWorker;
import tech.kayys.numou.foundation.application.runtime.RuntimeComponent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of ShutdownCoordinator implementing the 10-step graceful shutdown algorithm.
 */
public final class DefaultShutdownCoordinator implements ShutdownCoordinator {

    private final RuntimeAdmission admission;
    private final List<ManagedWorker> workers;
    private final List<RuntimeComponent> components;
    private final ShutdownPolicy policy;
    private final Runnable onNotReadyAction;

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private final AtomicBoolean shutdownComplete = new AtomicBoolean(false);
    private volatile Uni<ShutdownResult> cachedShutdownUni;

    public DefaultShutdownCoordinator(
            RuntimeAdmission admission,
            Collection<ManagedWorker> workers,
            Collection<RuntimeComponent> components,
            ShutdownPolicy policy,
            Runnable onNotReadyAction) {

        this.admission = Objects.requireNonNull(admission, "admission cannot be null");
        this.workers = workers != null ? new ArrayList<>(workers) : new ArrayList<>();
        this.components = components != null ? new ArrayList<>(components) : new ArrayList<>();
        this.policy = policy != null ? policy : ShutdownPolicy.defaultPolicy();
        this.onNotReadyAction = onNotReadyAction != null ? onNotReadyAction : () -> {};
    }

    @Override
    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    @Override
    public boolean isShutdownComplete() {
        return shutdownComplete.get();
    }

    @Override
    public synchronized Uni<ShutdownResult> shutdown() {
        if (cachedShutdownUni != null) {
            return cachedShutdownUni;
        }

        shutdownStarted.set(true);
        ShutdownContext context = ShutdownContext.of(policy);

        cachedShutdownUni = Uni.createFrom().item(() -> executeShutdown(context));
        return cachedShutdownUni;
    }

    private ShutdownResult executeShutdown(ShutdownContext context) {
        List<ShutdownFailure> failures = new ArrayList<>();

        // 1. Mark NOT_READY (readiness drops first before anything else)
        try {
            onNotReadyAction.run();
        } catch (Throwable t) {
            failures.add(new ShutdownFailure(ShutdownPhase.AVAILABILITY, "readiness", t.getMessage(), t));
        }

        // 2. ADMISSION & DRAIN
        try {
            admission.beginDraining();
            admission.drain().await().atMost(policy.drainTimeout());
        } catch (Throwable t) {
            failures.add(new ShutdownFailure(ShutdownPhase.DRAIN, "admission", t.getMessage(), t));
        } finally {
            admission.close();
        }

        // 3. WORKERS (stop in reverse order)
        List<ManagedWorker> reversedWorkers = new ArrayList<>(workers);
        Collections.reverse(reversedWorkers);
        for (ManagedWorker worker : reversedWorkers) {
            try {
                worker.stop().await().atMost(policy.consumerTimeout());
            } catch (Throwable t) {
                failures.add(new ShutdownFailure(ShutdownPhase.CONSUMERS, worker.id(), t.getMessage(), t));
            }
        }

        // 4. COMPONENTS (stop in reverse order)
        List<RuntimeComponent> reversedComponents = new ArrayList<>(components);
        Collections.reverse(reversedComponents);
        for (RuntimeComponent comp : reversedComponents) {
            try {
                comp.stop().await().atMost(policy.infrastructureTimeout());
            } catch (Throwable t) {
                failures.add(new ShutdownFailure(ShutdownPhase.INFRASTRUCTURE, comp.id(), t.getMessage(), t));
            }
        }

        shutdownComplete.set(true);
        return new ShutdownResult(failures.isEmpty(), context.startedAt(), Instant.now(), failures);
    }
}
